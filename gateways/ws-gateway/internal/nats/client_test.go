package nats

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/joaosimsic/hermes/ws-gateway/internal/protocol"
	"github.com/joaosimsic/hermes/ws-gateway/internal/resilience"
	"github.com/joaosimsic/hermes/ws-gateway/internal/types"
	"github.com/nats-io/nats-server/v2/server"
	natsserver "github.com/nats-io/nats-server/v2/test"
	"github.com/nats-io/nats.go"
	"go.uber.org/zap"
)

func runTestServer() *server.Server {
	opts := natsserver.DefaultTestOptions
	opts.Port = -1
	return natsserver.RunServer(&opts)
}

func TestNATSClient_Integration(t *testing.T) {
	s := runTestServer()
	defer s.Shutdown()

	logger := zap.NewNop()
	cbConfig := resilience.DefaultCircuitBreakerConfig()
	client, err := NewClient(s.ClientURL(), logger, cbConfig)
	if err != nil {
		t.Fatalf("Failed to connect: %v", err)
	}
	defer client.Close()

	t.Run("PublishMessage", func(t *testing.T) {
		sub, err := client.conn.Subscribe(SubjectSendMessage, func(m *nats.Msg) {
			var p map[string]any
			if err := json.Unmarshal(m.Data, &p); err != nil {
				t.Errorf("failed to unmarshal message: %v", err)
				return
			}
			if p["content"] != "hello" {
				t.Errorf("expected content hello, got %v", p["content"])
			}
		})
		if err != nil {
			t.Fatal(err)
		}
		defer func() {
			if err := sub.Unsubscribe(); err != nil {
				t.Logf("failed to unsubscribe: %v", err)
			}
		}()

		payload := &protocol.SendMessagePayload{
			ConversationID: "conv_1",
			Content:        "hello",
			ClientMsgID:    "msg_1",
		}

		ctx := types.MessageContext{
			UserID:  "user_1",
			Email:   "user1@example.com",
			TraceID: "trace_123",
		}

		if err := client.PublishMessage(ctx, payload); err != nil {
			t.Errorf("PublishMessage failed: %v", err)
		}
	})

	t.Run("PresenceBroadcast", func(t *testing.T) {
		done := make(chan bool)
		mockHandler := &mockNatsHandler{
			onPresence: func(p *protocol.PresencePayload) {
				if p.UserID == "user_1" && p.Status == "online" {
					done <- true
				}
			},
		}

		client.SetHandler(mockHandler)
		if err := client.SubscribeToPresenceBroadcast(); err != nil {
			t.Fatal(err)
		}

		if err := client.PublishPresence("user_1", "online", "trace_123"); err != nil {
			t.Errorf("PublishPresence failed: %v", err)
		}

		select {
		case <-done:
		case <-time.After(1 * time.Second):
			t.Error("Timed out waiting for presence broadcast")
		}
	})

	t.Run("CircuitBreakerState", func(t *testing.T) {
		state := client.CircuitBreakerState()
		if state != resilience.StateClosed {
			t.Errorf("expected circuit breaker to be Closed, got %s", state)
		}
	})

	t.Run("CircuitBreakerMetrics", func(t *testing.T) {
		state, failures, total, _ := client.CircuitBreakerMetrics()
		if state != resilience.StateClosed {
			t.Errorf("expected state Closed, got %s", state)
		}
		if failures < 0 {
			t.Errorf("failures should be non-negative, got %d", failures)
		}
		if total < 0 {
			t.Errorf("total should be non-negative, got %d", total)
		}
	})

	t.Run("HeadersIncluded", func(t *testing.T) {
		headersReceived := make(chan map[string]string, 1)
		sub, err := client.conn.Subscribe(SubjectSendMessage, func(m *nats.Msg) {
			var p map[string]any
			if err := json.Unmarshal(m.Data, &p); err != nil {
				t.Errorf("failed to unmarshal message: %v", err)
				return
			}
			if headers, ok := p["_headers"].(map[string]any); ok {
				h := make(map[string]string)
				for k, v := range headers {
					if s, ok := v.(string); ok {
						h[k] = s
					}
				}
				headersReceived <- h
			}
		})
		if err != nil {
			t.Fatal(err)
		}
		defer func() { _ = sub.Unsubscribe() }()

		payload := &protocol.SendMessagePayload{
			ConversationID: "conv_1",
			Content:        "hello",
			ClientMsgID:    "msg_1",
		}

		ctx := types.MessageContext{
			UserID:  "user_123",
			Email:   "test@example.com",
			TraceID: "trace_abc",
		}

		if err := client.PublishMessage(ctx, payload); err != nil {
			t.Errorf("PublishMessage failed: %v", err)
		}

		select {
		case headers := <-headersReceived:
			if headers["X-User-Id"] != "user_123" {
				t.Errorf("expected X-User-Id to be user_123, got %s", headers["X-User-Id"])
			}
			if headers["X-User-Email"] != "test@example.com" {
				t.Errorf("expected X-User-Email to be test@example.com, got %s", headers["X-User-Email"])
			}
			if headers["X-Trace-Id"] != "trace_abc" {
				t.Errorf("expected X-Trace-Id to be trace_abc, got %s", headers["X-Trace-Id"])
			}
		case <-time.After(1 * time.Second):
			t.Error("Timed out waiting for message with headers")
		}
	})
}

type mockNatsHandler struct {
	onPresence func(*protocol.PresencePayload)
}

func (m *mockNatsHandler) OnMessageReceived(u string, p *protocol.MessagePayload)         {}
func (m *mockNatsHandler) OnTypingReceived(u string, p *protocol.TypingPayload)           {}
func (m *mockNatsHandler) OnReadReceiptReceived(u string, p *protocol.ReadReceiptPayload) {}
func (m *mockNatsHandler) OnAckReceived(u string, p *protocol.AckPayload)                 {}
func (m *mockNatsHandler) OnPresenceReceived(p *protocol.PresencePayload) {
	if m.onPresence != nil {
		m.onPresence(p)
	}
}
