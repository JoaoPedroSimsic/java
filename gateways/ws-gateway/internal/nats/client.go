package nats

import (
	"encoding/json"
	"fmt"
	"sync"

	"github.com/joaosimsic/hermes/ws-gateway/internal/protocol"
	"github.com/joaosimsic/hermes/ws-gateway/internal/resilience"
	"github.com/joaosimsic/hermes/ws-gateway/internal/types"
	"github.com/nats-io/nats.go"
	"go.uber.org/zap"
)

const (
	SubjectChatUser          = "chat.user.%s"
	SubjectChatConversation  = "chat.conversation.%s"
	SubjectPresence          = "presence.%s"
	SubjectPresenceBroadcast = "presence.broadcast"
	SubjectUserOnline        = "user.online"
	SubjectSendMessage       = "chat.send"
	SubjectMarkRead          = "chat.read"
)

type MessageHandler interface {
	OnMessageReceived(targetUserID string, msg *protocol.MessagePayload)
	OnTypingReceived(targetUserID string, payload *protocol.TypingPayload)
	OnReadReceiptReceived(targetUserID string, payload *protocol.ReadReceiptPayload)
	OnPresenceReceived(payload *protocol.PresencePayload)
	OnAckReceived(targetUserID string, payload *protocol.AckPayload)
}

type Client struct {
	conn           *nats.Conn
	logger         *zap.Logger
	mu             sync.RWMutex
	handler        MessageHandler
	subs           []*nats.Subscription
	circuitBreaker *resilience.CircuitBreaker
}

func NewClient(url string, logger *zap.Logger, cbConfig resilience.CircuitBreakerConfig) (*Client, error) {
	cb := resilience.NewCircuitBreaker("nats", cbConfig)
	cb.SetStateChangeNotifier(func(name string, from, to resilience.State) {
		logger.Info("circuit breaker state changed",
			zap.String("breaker", name),
			zap.String("from", from.String()),
			zap.String("to", to.String()),
		)
	})

	opts := []nats.Option{
		nats.RetryOnFailedConnect(true),
		nats.MaxReconnects(-1),
		nats.ReconnectWait(nats.DefaultReconnectWait),
		nats.DisconnectErrHandler(func(_ *nats.Conn, err error) {
			if err != nil {
				logger.Warn("NATS disconnected", zap.Error(err))
			}
		}),
		nats.ReconnectHandler(func(_ *nats.Conn) {
			logger.Info("NATS reconnected")
			cb.Reset()
		}),
	}

	nc, err := nats.Connect(url, opts...)
	if err != nil {
		return nil, fmt.Errorf("nats: failed to connect to %s: %w", url, err)
	}

	return &Client{
		conn:           nc,
		logger:         logger.Named("nats_client"),
		circuitBreaker: cb,
	}, nil
}

func (c *Client) SetHandler(handler MessageHandler) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.handler = handler
}

func (c *Client) SubscribeToUserMessages(userID string) error {
	subject := fmt.Sprintf(SubjectChatUser, userID)
	sub, err := c.conn.Subscribe(subject, func(msg *nats.Msg) {
		c.handleUserMessage(userID, msg)
	})
	if err != nil {
		return fmt.Errorf("nats: failed to subscribe to user %s: %w", userID, err)
	}

	c.trackSubscription(sub)
	return nil
}

func (c *Client) SubscribeToPresenceBroadcast() error {
	sub, err := c.conn.Subscribe(SubjectPresenceBroadcast, func(msg *nats.Msg) {
		var payload protocol.PresencePayload
		if err := json.Unmarshal(msg.Data, &payload); err != nil {
			c.logger.Error("failed to unmarshal presence broadcast", zap.Error(err))
			return
		}

		c.mu.RLock()
		h := c.handler
		c.mu.RUnlock()

		if h != nil {
			h.OnPresenceReceived(&payload)
		}
	})
	if err != nil {
		return fmt.Errorf("nats: failed to subscribe to presence broadcast: %w", err)
	}

	c.trackSubscription(sub)
	return nil
}

func (c *Client) handleUserMessage(userID string, msg *nats.Msg) {
	var envelope protocol.Envelope
	if err := json.Unmarshal(msg.Data, &envelope); err != nil {
		c.logger.Warn("failed to unmarshal NATS envelope", zap.Error(err), zap.String("userID", userID))
		return
	}

	c.mu.RLock()
	h := c.handler
	c.mu.RUnlock()

	if h == nil {
		return
	}

	switch envelope.Type {
	case protocol.TypeMessage:
		unmarshalAndHandle(c, envelope.Payload, userID, h.OnMessageReceived)
	case protocol.TypeTyping:
		unmarshalAndHandle(c, envelope.Payload, userID, h.OnTypingReceived)
	case protocol.TypeReadReceipt:
		unmarshalAndHandle(c, envelope.Payload, userID, h.OnReadReceiptReceived)
	case protocol.TypeAck:
		unmarshalAndHandle(c, envelope.Payload, userID, h.OnAckReceived)
	default:
		c.logger.Debug("received unknown envelope type", zap.String("type", string(envelope.Type)))
	}
}

func (c *Client) PublishMessage(ctx types.MessageContext, msg *protocol.SendMessagePayload) error {
	payload := map[string]any{
		"senderId":       ctx.UserID,
		"senderEmail":    ctx.Email,
		"conversationId": msg.ConversationID,
		"content":        msg.Content,
		"mediaId":        msg.MediaID,
		"clientMsgId":    msg.ClientMsgID,
		"_headers": map[string]string{
			"X-User-Id":    ctx.UserID,
			"X-User-Email": ctx.Email,
			"X-Trace-Id":   ctx.TraceID,
		},
	}
	return c.publish(SubjectSendMessage, payload)
}

func (c *Client) PublishTyping(ctx types.MessageContext, conversationID string) error {
	subject := fmt.Sprintf(SubjectChatConversation, conversationID)

	payload := map[string]any{
		"type": string(protocol.TypeTyping),
		"payload": protocol.TypingPayload{
			ConversationID: conversationID,
			UserID:         ctx.UserID,
		},
		"_headers": map[string]string{
			"X-User-Id":    ctx.UserID,
			"X-User-Email": ctx.Email,
			"X-Trace-Id":   ctx.TraceID,
		},
	}

	return c.publish(subject, payload)
}

func (c *Client) PublishMarkRead(ctx types.MessageContext, conversationID, messageID string) error {
	payload := map[string]any{
		"userId":         ctx.UserID,
		"conversationId": conversationID,
		"messageId":      messageID,
		"_headers": map[string]string{
			"X-User-Id":    ctx.UserID,
			"X-User-Email": ctx.Email,
			"X-Trace-Id":   ctx.TraceID,
		},
	}
	return c.publish(SubjectMarkRead, payload)
}

func (c *Client) PublishPresence(userID, status, traceID string) error {
	payload := map[string]any{
		"userId": userID,
		"status": status,
		"_headers": map[string]string{
			"X-User-Id":  userID,
			"X-Trace-Id": traceID,
		},
	}
	return c.publish(SubjectPresenceBroadcast, payload)
}

func (c *Client) PublishUserOnline(userID, traceID string) error {
	payload := map[string]any{
		"userId": userID,
		"_headers": map[string]string{
			"X-User-Id":  userID,
			"X-Trace-Id": traceID,
		},
	}
	return c.publish(SubjectUserOnline, payload)
}

func (c *Client) Close() {
	c.mu.Lock()
	defer c.mu.Unlock()

	for _, sub := range c.subs {
		if err := sub.Unsubscribe(); err != nil {
			c.logger.Error("failed to unsubscribe", zap.String("subject", sub.Subject), zap.Error(err))
		}
	}
	c.subs = nil
	c.conn.Close()
}

func (c *Client) trackSubscription(sub *nats.Subscription) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.subs = append(c.subs, sub)
}

func (c *Client) publish(subject string, v any) error {
	if err := c.circuitBreaker.Allow(); err != nil {
		c.logger.Warn("circuit breaker rejected request",
			zap.String("subject", subject),
			zap.Error(err),
		)
		return fmt.Errorf("nats: circuit breaker open: %w", err)
	}

	data, err := json.Marshal(v)
	if err != nil {
		return fmt.Errorf("nats: marshal error for subject %s: %w", subject, err)
	}

	if err := c.conn.Publish(subject, data); err != nil {
		c.circuitBreaker.RecordFailure()
		return fmt.Errorf("nats: publish error for subject %s: %w", subject, err)
	}

	c.circuitBreaker.RecordSuccess()
	return nil
}

func (c *Client) CircuitBreakerState() resilience.State {
	return c.circuitBreaker.State()
}

func (c *Client) CircuitBreakerMetrics() (state resilience.State, failures, total int, failureRate float64) {
	return c.circuitBreaker.Metrics()
}

func unmarshalAndHandle[T any](c *Client, data []byte, userID string, next func(string, *T)) {
	var p T
	if err := json.Unmarshal(data, &p); err != nil {
		c.logger.Error("failed to unmarshal payload",
			zap.Error(err),
			zap.String("target_type", fmt.Sprintf("%T", p)),
		)
		return
	}
	next(userID, &p)
}
