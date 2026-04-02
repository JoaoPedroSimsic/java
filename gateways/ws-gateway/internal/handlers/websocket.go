package handlers

import (
	"context"
	"net/http"
	"time"

	"github.com/gorilla/websocket"
	"github.com/joaosimsic/hermes/ws-gateway/internal/config"
	"github.com/joaosimsic/hermes/ws-gateway/internal/hub"
	"github.com/joaosimsic/hermes/ws-gateway/internal/ratelimit"
	"github.com/joaosimsic/hermes/ws-gateway/pkg/jwt"
	"go.uber.org/zap"
)

type TokenValidator interface {
	Validate(ctx context.Context, token string) (*jwt.Claims, error)
}

type WebSocketHandler struct {
	cfg          *config.Config
	hub          *hub.Hub
	validator    TokenValidator
	logger       *zap.Logger
	upgrader     websocket.Upgrader
	redisLimiter *ratelimit.RedisRateLimiter
}

func NewWebSocketHandler(cfg *config.Config, h *hub.Hub, v TokenValidator, l *zap.Logger, redisLimiter *ratelimit.RedisRateLimiter) *WebSocketHandler {
	return &WebSocketHandler{
		cfg:          cfg,
		hub:          h,
		validator:    v,
		logger:       l,
		redisLimiter: redisLimiter,
		upgrader: websocket.Upgrader{
			ReadBufferSize:  1024,
			WriteBufferSize: 1024,
			CheckOrigin: func(r *http.Request) bool {
				if cfg.Profile == "dev" {
					return true
				}

				origin := r.Header.Get("Origin")

				return origin == cfg.GetAllowedOrigin()
			},
		},
	}
}

func (h *WebSocketHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	traceID, _ := r.Context().Value(TraceIDKey).(string)

	w.Header().Set(TraceIDHeader, traceID)

	h.logger.Debug("WebSocket connection attempt",
		zap.String("cookie_header", r.Header.Get("Cookie")),
		zap.Any("cookies", r.Cookies()),
		zap.String("protocols", r.Header.Get("Sec-WebSocket-Protocol")),
		zap.String("trace_id", traceID),
	)

	token := h.extractToken(r)

	if token == "" {
		h.logger.Warn("No token found in request")
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	claims, err := h.validator.Validate(r.Context(), token)
	if err != nil {
		h.logger.Warn("JWT validation failed",
			zap.String("trace_id", traceID),
			zap.Error(err))
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var responseHeader http.Header
	if protocols := websocket.Subprotocols(r); len(protocols) > 0 {
		responseHeader = http.Header{}
		responseHeader.Set("Sec-WebSocket-Protocol", protocols[0])
		responseHeader.Set(TraceIDHeader, traceID)
	}

	conn, err := h.upgrader.Upgrade(w, r, responseHeader)
	if err != nil {
		h.logger.Error("WebSocket upgrade failed",
			zap.String("trace_id", traceID),
			zap.Error(err))
		return
	}

	var maxDuration time.Duration
	if h.cfg.ConnectionMaxDurationMinutes > 0 {
		maxDuration = time.Duration(h.cfg.ConnectionMaxDurationMinutes) * time.Minute
	}

	client := hub.NewClient(hub.ClientOptions{
		Hub:            h.hub,
		Conn:           conn,
		UserID:         claims.Subject,
		Email:          claims.Email,
		TraceID:        traceID,
		RateLimit:      h.cfg.RateLimitAuthenticated,
		RateLimitBurst: h.cfg.RateLimitAuthenticatedBurst,
		Logger:         h.logger,
		RedisLimiter:   h.redisLimiter,
		MaxDuration:    maxDuration,
	})
	h.hub.Register(client)

	go client.WritePump()
	go client.ReadPump()
}

func (h *WebSocketHandler) extractToken(r *http.Request) string {
	if protocols := websocket.Subprotocols(r); len(protocols) > 0 {
		for _, p := range protocols {
			if len(p) > 7 && p[:7] == "bearer." {
				return p[7:]
			}
		}
	}
	if cookie, err := r.Cookie("access_token"); err == nil {
		return cookie.Value
	}
	if token := r.URL.Query().Get("token"); token != "" {
		return token
	}
	return ""
}

type HealthHandler struct{}

func (h *HealthHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"healthy","timestamp":"` + time.Now().Format(time.RFC3339) + `"}`))
}
