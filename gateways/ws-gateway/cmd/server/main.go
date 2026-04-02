package main

import (
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/joaosimsic/hermes/ws-gateway/internal/config"
	"github.com/joaosimsic/hermes/ws-gateway/internal/handlers"
	"github.com/joaosimsic/hermes/ws-gateway/internal/hub"
	"github.com/joaosimsic/hermes/ws-gateway/internal/nats"
	"github.com/joaosimsic/hermes/ws-gateway/internal/ratelimit"
	"github.com/joaosimsic/hermes/ws-gateway/internal/resilience"
	"github.com/joaosimsic/hermes/ws-gateway/pkg/jwt"
	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "failed to load config: %v\n", err)
		os.Exit(1)
	}

	logger := initLogger(cfg.LogLevel)
	defer func() { _ = logger.Sync() }()

	logger.Info("starting ws-gateway",
		zap.Int("port", cfg.ServerPort),
		zap.String("nats_url", cfg.NatsURL),
		zap.String("redis_addr", cfg.GetRedisAddr()),
	)

	cbConfig := resilience.CircuitBreakerConfig{
		SlidingWindowSize:                            cfg.CircuitBreakerSlidingWindowSize,
		MinimumNumberOfCalls:                         cfg.CircuitBreakerMinimumNumberOfCalls,
		FailureRateThreshold:                         float64(cfg.CircuitBreakerFailureRateThreshold),
		WaitDurationInOpenState:                      time.Duration(cfg.CircuitBreakerWaitDurationInOpenStateSeconds) * time.Second,
		PermittedNumberOfCallsInHalfOpenState:        cfg.CircuitBreakerPermittedNumberOfCallsInHalfOpenState,
		AutomaticTransitionFromOpenToHalfOpenEnabled: true,
	}

	natsClient, err := nats.NewClient(cfg.NatsURL, logger, cbConfig)
	if err != nil {
		logger.Fatal("failed to connect to NATS", zap.Error(err))
	}
	defer natsClient.Close()

	var redisLimiter *ratelimit.RedisRateLimiter
	if cfg.RedisHost != "" {
		redisClient := redis.NewClient(&redis.Options{
			Addr: cfg.GetRedisAddr(),
		})
		redisLimiter = ratelimit.NewRedisRateLimiter(redisClient, time.Second)
		defer func() { _ = redisLimiter.Close() }()
		logger.Info("Redis rate limiter initialized")
	}

	jwtValidator := jwt.NewValidator(
		cfg.GetJwksURL(),
		cfg.GetJwtIssuer(),
		time.Duration(cfg.JwksCacheTTL)*time.Second,
	)

	h := hub.NewHub(natsClient, logger)
	natsClient.SetHandler(h)

	if err := natsClient.SubscribeToPresenceBroadcast(); err != nil {
		logger.Fatal("failed to subscribe to presence broadcast", zap.Error(err))
	}

	go h.Run()

	wsHandler := handlers.NewWebSocketHandler(cfg, h, jwtValidator, logger, redisLimiter)
	healthHandler := &handlers.HealthHandler{}

	mux := http.NewServeMux()
	mux.Handle("/ws", handlers.TraceMiddleware(wsHandler))
	mux.Handle("/health", healthHandler)
	mux.Handle("/healthz", healthHandler)

	server := &http.Server{
		Addr:         fmt.Sprintf(":%d", cfg.ServerPort),
		Handler:      mux,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		logger.Info("HTTP server listening", zap.String("addr", server.Addr))
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatal("HTTP server error", zap.Error(err))
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	logger.Info("shutting down server...")
}

func initLogger(level string) *zap.Logger {
	var lvl zapcore.Level
	switch level {
	case "debug":
		lvl = zapcore.DebugLevel
	case "info":
		lvl = zapcore.InfoLevel
	case "warn":
		lvl = zapcore.WarnLevel
	case "error":
		lvl = zapcore.ErrorLevel
	default:
		lvl = zapcore.InfoLevel
	}

	cfg := zap.Config{
		Level:            zap.NewAtomicLevelAt(lvl),
		Development:      false,
		Encoding:         "json",
		EncoderConfig:    zap.NewProductionEncoderConfig(),
		OutputPaths:      []string{"stdout"},
		ErrorOutputPaths: []string{"stderr"},
	}

	logger, _ := cfg.Build()
	return logger
}
