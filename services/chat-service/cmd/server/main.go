package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	chathttp "github.com/joaosimsic/hermes/chat-service/internal/adapters/input/http"
	dynamodbadapter "github.com/joaosimsic/hermes/chat-service/internal/adapters/output/dynamodb"
	"github.com/joaosimsic/hermes/chat-service/internal/config"
	"github.com/joaosimsic/hermes/chat-service/internal/core/services"
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

	logger.Info("starting chat-service",
		zap.Int("port", cfg.ServerPort),
		zap.String("profile", cfg.Profile),
		zap.String("dynamo_endpoint", cfg.DynamoEndpoint),
		zap.String("nats_url", cfg.NatsURL),
	)

	ctx := context.Background()
	store, err := dynamodbadapter.NewStore(ctx, cfg.DynamoEndpoint, cfg.DynamoTable)
	if err != nil {
		logger.Fatal("failed to connect to dynamodb", zap.Error(err))
	}

	convRepo := dynamodbadapter.NewConversationRepository(store)
	msgRepo := dynamodbadapter.NewMessageRepository(store)
	readRepo := dynamodbadapter.NewReadReceiptRepository(store)
	presenceRepo := dynamodbadapter.NewPresenceRepository(store)
	userRepo := dynamodbadapter.NewUserRepository(store)

	chatSvc := services.NewChatService(convRepo, msgRepo, readRepo, presenceRepo, userRepo, nil, logger)
	_ = services.NewUserSyncService(userRepo, logger)
	router := chathttp.NewRouter(chatSvc, logger)

	server := &http.Server{
		Addr:         fmt.Sprintf(":%d", cfg.ServerPort),
		Handler:      router,
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
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(ctx); err != nil {
		logger.Error("server shutdown failed", zap.Error(err))
	}
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
