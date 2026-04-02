package hub

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/joaosimsic/hermes/ws-gateway/internal/protocol"
	"github.com/joaosimsic/hermes/ws-gateway/internal/types"
	"go.uber.org/zap"
)

type MockPublisher struct {
	MsgChan chan bool
}

func (m *MockPublisher) PublishMessage(ctx types.MessageContext, msg *protocol.SendMessagePayload) error {
	if m.MsgChan != nil {
		m.MsgChan <- true
	}
	return nil
}
func (m *MockPublisher) PublishTyping(ctx types.MessageContext, conversationID string) error {
	return nil
}
func (m *MockPublisher) PublishMarkRead(ctx types.MessageContext, conversationID, messageID string) error {
	return nil
}
func (m *MockPublisher) PublishPresence(userID, status, traceID string) error { return nil }
func (m *MockPublisher) PublishUserOnline(userID, traceID string) error       { return nil }

func TestClient_ReadPump(t *testing.T) {
	logger := zap.NewNop()
	done := make(chan bool, 1)
	h := NewHub(&MockPublisher{MsgChan: done}, logger)

	go h.Run()

	s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}
		conn, _ := upgrader.Upgrade(w, r, nil)

		client := NewClient(ClientOptions{
			Hub:            h,
			Conn:           conn,
			UserID:         "user_123",
			Email:          "test@example.com",
			TraceID:        "trace-1",
			RateLimit:      100,
			RateLimitBurst: 10,
			Logger:         logger,
		})
		h.register <- client
		client.ReadPump()
	}))
	defer s.Close()

	u := "ws" + strings.TrimPrefix(s.URL, "http")
	ws, _, _ := websocket.DefaultDialer.Dial(u, nil)
	defer func() { _ = ws.Close() }()

	msg := protocol.Envelope{
		Type:    protocol.TypeSendMessage,
		Payload: json.RawMessage(`{"conversationId":"conv_1","content":"hello"}`),
	}
	data, _ := json.Marshal(msg)
	if err := ws.WriteMessage(websocket.TextMessage, data); err != nil {
		t.Fatalf("Failed to write message: %v", err)
	}

	select {
	case <-done:
	case <-time.After(1 * time.Second):
		t.Fatal("Timeout waiting for message to be published to NATS")
	}
}

func TestClient_WritePump(t *testing.T) {
	logger := zap.NewNop()
	h := NewHub(&MockPublisher{}, logger)

	s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}
		conn, _ := upgrader.Upgrade(w, r, nil)

		client := NewClient(ClientOptions{
			Hub:            h,
			Conn:           conn,
			UserID:         "user_123",
			Email:          "test@example.com",
			TraceID:        "trace-1",
			RateLimit:      100,
			RateLimitBurst: 10,
			Logger:         logger,
		})

		go client.WritePump()
		client.Send([]byte("ping-from-server"))

		time.Sleep(100 * time.Millisecond)
	}))
	defer s.Close()

	u := "ws" + strings.TrimPrefix(s.URL, "http")
	ws, _, _ := websocket.DefaultDialer.Dial(u, nil)

	_, p, err := ws.ReadMessage()
	if err != nil {
		t.Fatalf("Failed to read: %v", err)
	}

	_ = ws.Close()

	if string(p) != "ping-from-server" {
		t.Errorf("Expected ping-from-server, got %s", string(p))
	}
}
