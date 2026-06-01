package protocol

import (
	"encoding/json"
	"errors"
	"fmt"
	"time"
)

type MessageType string

const (
	TypeSendMessage   MessageType = "send_message"
	TypeMarkRead      MessageType = "mark_read"
	TypePing          MessageType = "ping"
	TypeMessage       MessageType = "message"
	TypeTyping        MessageType = "typing"
	TypeReadReceipt   MessageType = "read_receipt"
	TypePresence      MessageType = "presence"
	TypeAck           MessageType = "ack"
	TypeError         MessageType = "error"
	TypePong          MessageType = "pong"
	TypeRateLimitInfo MessageType = "rate_limit_info"
	TypeSystem        MessageType = "system"
)

type Validator interface {
	Validate() error
}

type Envelope struct {
	Type    MessageType     `json:"type"`
	Payload json.RawMessage `json:"payload,omitempty"`
}

func NewEnvelope(msgType MessageType, payload any) (*Envelope, error) {
	if v, ok := payload.(Validator); ok {
		if err := v.Validate(); err != nil {
			return nil, fmt.Errorf("protocol: validation failed for %s: %w", msgType, err)
		}
	}

	data, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("protocol: marshal error: %w", err)
	}

	return &Envelope{
		Type:    msgType,
		Payload: data,
	}, nil
}

func (e *Envelope) Bytes() ([]byte, error) {
	return json.Marshal(e)
}

type SendMessagePayload struct {
	ConversationID string `json:"conversationId"`
	Content        string `json:"content"`
	MediaID        string `json:"mediaId,omitempty"`
	ClientMsgID    string `json:"clientMsgId"`
}

func (p SendMessagePayload) Validate() error {
	if p.ConversationID == "" {
		return errors.New("conversationId is required")
	}
	if p.Content == "" && p.MediaID == "" {
		return errors.New("message must have content or media")
	}
	return nil
}

type MessagePayload struct {
	ConversationID string    `json:"conversationId"`
	MessageID      string    `json:"messageId"`
	SenderID       string    `json:"senderId"`
	Content        string    `json:"content"`
	MediaID        string    `json:"mediaId,omitempty"`
	CreatedAt      time.Time `json:"createdAt"`
}

type TypingPayload struct {
	ConversationID string `json:"conversationId"`
	UserID         string `json:"userId,omitempty"`
}

type MarkReadPayload struct {
	ConversationID string `json:"conversationId"`
	MessageID      string `json:"messageId"`
}

type ReadReceiptPayload struct {
	ConversationID string `json:"conversationId"`
	UserID         string `json:"userId"`
	MessageID      string `json:"messageId"`
}

type PresencePayload struct {
	UserID       string     `json:"userId"`
	Status       string     `json:"status"`
	LastActiveAt *time.Time `json:"lastActiveAt,omitempty"`
}

type AckPayload struct {
	ClientMsgID string `json:"clientMsgId"`
	MessageID   string `json:"messageId"`
}

type ErrorPayload struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type RateLimitInfoPayload struct {
	Limit     int `json:"limit"`
	Remaining int `json:"remaining"`
	ResetIn   int `json:"resetIn"`
}

type SystemPayload struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// REST shapes

type CreateConversationRequest struct {
	Type          string   `json:"type"`
	ParticipantID string   `json:"participantId,omitempty"`
	Name          string   `json:"name,omitempty"`
	MemberIDs     []string `json:"memberIds,omitempty"`
}

type MemberResponse struct {
	UserID       string     `json:"userId"`
	Email        string     `json:"email"`
	Name         string     `json:"name"`
	LastActiveAt *time.Time `json:"lastActiveAt,omitempty"`
}

type ConversationResponse struct {
	ID            string           `json:"id"`
	Type          string           `json:"type"`
	Name          *string          `json:"name"`
	Members       []MemberResponse `json:"members"`
	CreatedAt     time.Time        `json:"createdAt"`
	LastMessageAt *time.Time       `json:"lastMessageAt"`
	Preview       *string          `json:"preview"`
}

type InboxConversationResponse struct {
	ConversationResponse
	UnreadCount         int     `json:"unreadCount"`
	LastReadMessageID   *string `json:"lastReadMessageId"`
}

type InboxResponse struct {
	Conversations []InboxConversationResponse `json:"conversations"`
}

type MessageHistoryItem struct {
	MessageID string    `json:"messageId"`
	SenderID  string    `json:"senderId"`
	Content   string    `json:"content"`
	MediaID   *string   `json:"mediaId"`
	CreatedAt time.Time `json:"createdAt"`
}

type MessageHistoryResponse struct {
	Messages   []MessageHistoryItem `json:"messages"`
	NextCursor *string              `json:"nextCursor"`
}
