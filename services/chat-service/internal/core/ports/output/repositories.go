package output

import (
	"context"

	"github.com/joaosimsic/hermes/chat-service/internal/core/domain"
)

type MessageRepository interface {
	PutMessage(ctx context.Context, msg domain.Message) error
	GetDedupMessageID(ctx context.Context, conversationID, clientMsgID string) (string, bool, error)
	ListMessages(ctx context.Context, conversationID, cursor string, limit int) ([]domain.Message, string, error)
}

type ConversationRepository interface {
	GetOrCreateDirect(ctx context.Context, userA, userB string) (*domain.Conversation, bool, error)
	CreateGroup(ctx context.Context, creatorID, name string, memberIDs []string) (*domain.Conversation, error)
	GetMeta(ctx context.Context, conversationID string) (*domain.Conversation, error)
	IsMember(ctx context.Context, userID, conversationID string) (bool, error)
	ListInbox(ctx context.Context, userID string) ([]domain.InboxEntry, error)
	BumpConversationMeta(ctx context.Context, conversationID string, lastMessageAt domain.Message) error
	IncrementUnread(ctx context.Context, conversationID string, memberIDs []string, excludeUserID string) error
	ResetUnread(ctx context.Context, userID, conversationID, lastReadMessageID string) error
}

type ReadReceiptRepository interface {
	Upsert(ctx context.Context, conversationID, userID, messageID string) error
}

type PresenceRepository interface {
	Upsert(ctx context.Context, presence domain.Presence) error
	Get(ctx context.Context, userID string) (*domain.Presence, error)
}

type UserRepository interface {
	UpsertProfile(ctx context.Context, profile domain.UserProfile) error
	DeleteProfile(ctx context.Context, userID string) error
	GetProfile(ctx context.Context, userID string) (*domain.UserProfile, error)
	RecordEvent(ctx context.Context, aggregateID, occurredAt string) (bool, error)
}

type MessagePublisher interface {
	PublishToUser(ctx context.Context, userID string, envelope []byte) error
}
