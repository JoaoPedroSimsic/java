package dynamodb

import "time"

type conversationMetaItem struct {
	PK            string    `dynamodbav:"PK"`
	SK            string    `dynamodbav:"SK"`
	Type          string    `dynamodbav:"type"`
	Name          string    `dynamodbav:"name,omitempty"`
	CreatedBy     string    `dynamodbav:"createdBy"`
	Members       []string  `dynamodbav:"members"`
	CreatedAt     time.Time `dynamodbav:"createdAt"`
	LastMessageAt time.Time `dynamodbav:"lastMessageAt,omitempty"`
	Preview       string    `dynamodbav:"preview,omitempty"`
}

type inboxItem struct {
	PK                string    `dynamodbav:"PK"`
	SK                string    `dynamodbav:"SK"`
	LastMessageAt     time.Time `dynamodbav:"lastMessageAt,omitempty"`
	UnreadCount       int       `dynamodbav:"unreadCount"`
	LastReadMessageID string    `dynamodbav:"lastReadMessageId,omitempty"`
}

type messageItem struct {
	PK             string    `dynamodbav:"PK"`
	SK             string    `dynamodbav:"SK"`
	MessageID      string    `dynamodbav:"messageId"`
	SenderID       string    `dynamodbav:"senderId"`
	Content        string    `dynamodbav:"content"`
	MediaID        string    `dynamodbav:"mediaId,omitempty"`
	ClientMsgID    string    `dynamodbav:"clientMsgId"`
	CreatedAt      time.Time `dynamodbav:"createdAt"`
}

type dedupItem struct {
	PK        string `dynamodbav:"PK"`
	SK        string `dynamodbav:"SK"`
	MessageID string `dynamodbav:"messageId"`
}

type readReceiptItem struct {
	PK                string    `dynamodbav:"PK"`
	SK                string    `dynamodbav:"SK"`
	LastReadMessageID string    `dynamodbav:"lastReadMessageId"`
	ReadAt            time.Time `dynamodbav:"readAt"`
}

type presenceItem struct {
	PK           string    `dynamodbav:"PK"`
	SK           string    `dynamodbav:"SK"`
	Status       string    `dynamodbav:"status"`
	LastActiveAt time.Time `dynamodbav:"lastActiveAt"`
}

type profileItem struct {
	PK        string    `dynamodbav:"PK"`
	SK        string    `dynamodbav:"SK"`
	Email     string    `dynamodbav:"email"`
	Name      string    `dynamodbav:"name"`
	UpdatedAt time.Time `dynamodbav:"updatedAt"`
}

type eventItem struct {
	PK string `dynamodbav:"PK"`
	SK string `dynamodbav:"SK"`
}
