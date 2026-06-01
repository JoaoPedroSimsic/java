package dynamodb

import (
	"context"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/output"
)

type ReadReceiptRepository struct {
	store *Store
}

func NewReadReceiptRepository(store *Store) *ReadReceiptRepository {
	return &ReadReceiptRepository{store: store}
}

func (r *ReadReceiptRepository) Upsert(ctx context.Context, conversationID, userID, messageID string) error {
	item := readReceiptItem{
		PK:                convPK(conversationID),
		SK:                readSK(userID),
		LastReadMessageID: messageID,
		ReadAt:            time.Now().UTC(),
	}
	av, err := attributevalue.MarshalMap(item)
	if err != nil {
		return err
	}
	_, err = r.store.client.PutItem(ctx, &dynamodb.PutItemInput{
		TableName: aws.String(r.store.table),
		Item:      av,
	})
	return err
}

var _ output.ReadReceiptRepository = (*ReadReceiptRepository)(nil)
