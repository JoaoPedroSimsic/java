package domain

import "time"

type Message struct {
	ConversationID string
	MessageID      string
	SenderID       string
	Content        string
	MediaID        string
	ClientMsgID    string
	CreatedAt      time.Time
}
