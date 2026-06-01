package input

import (
	"context"

	"github.com/joaosimsic/hermes/chat-service/internal/protocol"
)

type ChatUseCase interface {
	SendMessage(ctx context.Context, cmd SendMessageCommand) error
	MarkRead(ctx context.Context, cmd MarkReadCommand) error
	HandleTyping(ctx context.Context, cmd TypingCommand) error
	HandlePresence(ctx context.Context, cmd PresenceCommand) error

	CreateConversation(ctx context.Context, userID string, req protocol.CreateConversationRequest) (*protocol.ConversationResponse, bool, error)
	ListConversations(ctx context.Context, userID string) (*protocol.InboxResponse, error)
	ListMessages(ctx context.Context, userID, conversationID, cursor string, limit int) (*protocol.MessageHistoryResponse, error)
}

type SendMessageCommand struct {
	SenderID       string
	SenderEmail    string
	ConversationID string
	Content        string
	MediaID        string
	ClientMsgID    string
}

type MarkReadCommand struct {
	UserID         string
	ConversationID string
	MessageID      string
}

type TypingCommand struct {
	ConversationID string
	UserID         string
}

type PresenceCommand struct {
	UserID string
	Status string
}

type UserEventCommand struct {
	AggregateID string
	Email       string
	Name        string
	OccurredAt  string
	EventType   string
}

type UserSyncUseCase interface {
	HandleUserEvent(ctx context.Context, cmd UserEventCommand) error
}
