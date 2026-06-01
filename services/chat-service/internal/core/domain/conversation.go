package domain

import "time"

type ConversationType string

const (
	ConversationDirect ConversationType = "direct"
	ConversationGroup  ConversationType = "group"
)

type Conversation struct {
	ID            string
	Type          ConversationType
	Name          string
	CreatedBy     string
	Members       []string
	CreatedAt     time.Time
	LastMessageAt *time.Time
	Preview       string
}

type InboxEntry struct {
	ConversationID      string
	LastMessageAt       *time.Time
	UnreadCount         int
	LastReadMessageID   string
}
