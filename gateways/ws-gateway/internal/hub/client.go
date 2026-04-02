package hub

import (
	"context"
	"encoding/json"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/joaosimsic/hermes/ws-gateway/internal/protocol"
	"github.com/joaosimsic/hermes/ws-gateway/internal/ratelimit"
	"go.uber.org/zap"
	"golang.org/x/time/rate"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 65536
)

type Client struct {
	hub            *Hub
	conn           *websocket.Conn
	send           chan []byte
	userID         string
	email          string
	traceID        string
	logger         *zap.Logger
	mu             sync.Mutex
	closed         bool
	closeOnce      sync.Once
	limiter        *rate.Limiter
	redisLimiter   *ratelimit.RedisRateLimiter
	rateLimit      int
	rateLimitBurst int
	connectedAt    time.Time
	maxDuration    time.Duration
}

type ClientOptions struct {
	Hub            *Hub
	Conn           *websocket.Conn
	UserID         string
	Email          string
	TraceID        string
	RateLimit      int
	RateLimitBurst int
	Logger         *zap.Logger
	RedisLimiter   *ratelimit.RedisRateLimiter
	MaxDuration    time.Duration
}

func NewClient(opts ClientOptions) *Client {
	return &Client{
		hub:            opts.Hub,
		conn:           opts.Conn,
		send:           make(chan []byte, 256),
		userID:         opts.UserID,
		email:          opts.Email,
		traceID:        opts.TraceID,
		logger:         opts.Logger.With(zap.String("user_id", opts.UserID), zap.String("trace_id", opts.TraceID)),
		limiter:        rate.NewLimiter(rate.Limit(opts.RateLimit), opts.RateLimitBurst),
		redisLimiter:   opts.RedisLimiter,
		rateLimit:      opts.RateLimit,
		rateLimitBurst: opts.RateLimitBurst,
		connectedAt:    time.Now(),
		maxDuration:    opts.MaxDuration,
	}
}

func (c *Client) UserID() string {
	return c.userID
}

func (c *Client) ReadPump() {
	defer c.Close()

	c.conn.SetReadLimit(maxMessageSize)

	_ = c.conn.SetReadDeadline(time.Now().Add(pongWait))

	c.conn.SetPongHandler(func(string) error {
		_ = c.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		if c.maxDuration > 0 && time.Since(c.connectedAt) > c.maxDuration {
			c.logger.Info("connection max duration exceeded, closing")
			c.sendSystemMessage("CONNECTION_TIMEOUT", "Connection duration limit exceeded")
			break
		}

		_, message, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				c.logger.Warn("websocket read error", zap.Error(err))
			}

			break
		}

		allowed, rateLimitResult := c.checkRateLimit()
		if !allowed {
			c.logger.Warn("rate limit exceeded, dropping message")
			c.sendError("RATE_LIMIT_EXCEEDED", "Too many messages")
			c.sendRateLimitInfo(rateLimitResult)
			continue
		}

		if rateLimitResult != nil {
			c.sendRateLimitInfo(rateLimitResult)
		}

		var envelope protocol.Envelope
		if err := json.Unmarshal(message, &envelope); err != nil {
			c.sendError("INVALID_MESSAGE", "Invalid message format")
			continue
		}

		c.handleMessage(&envelope)
	}
}

func (c *Client) checkRateLimit() (bool, *ratelimit.RateLimitResult) {
	if c.redisLimiter != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
		defer cancel()

		result, err := c.redisLimiter.Check(ctx, "user:"+c.userID, c.rateLimitBurst)
		if err != nil {
			c.logger.Warn("redis rate limit check failed, falling back to in-memory", zap.Error(err))
			return c.limiter.Allow(), nil
		}

		return result.Allowed, result
	}

	return c.limiter.Allow(), nil
}

func (c *Client) sendRateLimitInfo(result *ratelimit.RateLimitResult) {
	if result == nil {
		return
	}

	env, err := protocol.NewEnvelope(protocol.TypeRateLimitInfo, protocol.RateLimitInfoPayload{
		Limit:     result.Limit,
		Remaining: result.Remaining,
		ResetIn:   int(result.ResetIn.Seconds()),
	})
	if err != nil {
		c.logger.Error("failed to create rate limit info envelope", zap.Error(err))
		return
	}

	data, err := env.Bytes()
	if err != nil {
		c.logger.Error("failed to serialize rate limit info envelope", zap.Error(err))
		return
	}

	c.Send(data)
}

func (c *Client) sendSystemMessage(code, message string) {
	env, err := protocol.NewEnvelope(protocol.TypeSystem, protocol.SystemPayload{
		Code:    code,
		Message: message,
	})
	if err != nil {
		c.logger.Error("failed to create system envelope", zap.Error(err))
		return
	}

	data, err := env.Bytes()
	if err != nil {
		c.logger.Error("failed to serialize system envelope", zap.Error(err))
		return
	}

	c.Send(data)
}

func (c *Client) WritePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			_ = c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				_ = c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			w, err := c.conn.NextWriter(websocket.TextMessage)
			if err != nil {
				return
			}

			_, _ = w.Write(message)

			n := len(c.send)
			for range n {
				_, _ = w.Write([]byte{'\n'})
				_, _ = w.Write(<-c.send)
			}

			if err := w.Close(); err != nil {
				return
			}

		case <-ticker.C:
			_ = c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (c *Client) Send(data []byte) {
	c.mu.Lock()

	defer c.mu.Unlock()

	if c.closed {
		return
	}

	select {
	case c.send <- data:
	default:
		c.logger.Warn("send buffer full, dropping message")
	}
}

func (c *Client) Close() {
	c.closeOnce.Do(func() {
		c.mu.Lock()

		if c.closed {
			c.mu.Unlock()
			return
		}

		c.closed = true
		c.mu.Unlock()

		_ = c.conn.Close()
		close(c.send)
		c.hub.Unregister(c)
	})
}

func (c *Client) handleMessage(envelope *protocol.Envelope) {
	switch envelope.Type {
	case protocol.TypeSendMessage:
		c.handleSendMessage(envelope.Payload)
	case protocol.TypeTyping:
		c.handleTyping(envelope.Payload)
	case protocol.TypeMarkRead:
		c.handleMarkRead(envelope.Payload)
	case protocol.TypePong:
		c.sendPong()
	default:
		c.sendError("UNKNOWN_TYPE", "Unknown message type")
	}
}

func (c *Client) handleSendMessage(payload json.RawMessage) {
	var msg protocol.SendMessagePayload

	if err := json.Unmarshal(payload, &msg); err != nil {
		c.sendError("INVALID_PAYLOAD", "Invalid send_message payload")
		return
	}

	c.hub.HandleSendMessage(c, &msg)
}

func (c *Client) handleTyping(payload json.RawMessage) {
	var msg protocol.TypingPayload

	if err := json.Unmarshal(payload, &msg); err != nil {
		c.sendError("INVALID_PAYLOAD", "Invalid typing payload")
		return
	}

	c.hub.HandleTyping(c, &msg)
}

func (c *Client) handleMarkRead(payload json.RawMessage) {
	var msg protocol.MarkReadPayload

	if err := json.Unmarshal(payload, &msg); err != nil {
		c.sendError("INVALID_PAYLOAD", "Invalid mark_read payload")
		return
	}

	c.hub.HandleMarkRead(c, &msg)
}

func (c *Client) sendPong() {
	env, err := protocol.NewEnvelope(protocol.TypePong, nil)
	if err != nil {
		c.logger.Error("failed to create pong envelope", zap.Error(err))
		return
	}

	data, err := env.Bytes()
	if err != nil {
		c.logger.Error("failed serialize pong envelope", zap.Error(err))
		return
	}

	c.Send(data)
}

func (c *Client) sendError(code, message string) {
	env, err := protocol.NewEnvelope(protocol.TypeError, protocol.ErrorPayload{
		Code:    code,
		Message: message,
	})
	if err != nil {
		c.logger.Error("failed to create error envelope", zap.Error(err))
		return
	}

	data, err := env.Bytes()
	if err != nil {
		c.logger.Error("failed to serialize error envelope", zap.Error(err))
		return
	}

	c.Send(data)
}
