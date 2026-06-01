package dynamodb

import (
	"context"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
	"github.com/joaosimsic/hermes/chat-service/internal/core/domain"
	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/output"
)

type PresenceRepository struct {
	store *Store
}

func NewPresenceRepository(store *Store) *PresenceRepository {
	return &PresenceRepository{store: store}
}

func (r *PresenceRepository) Upsert(ctx context.Context, presence domain.Presence) error {
	item := presenceItem{
		PK:           userPK(presence.UserID),
		SK:           skPresence,
		Status:       presence.Status,
		LastActiveAt: presence.LastActiveAt,
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

func (r *PresenceRepository) Get(ctx context.Context, userID string) (*domain.Presence, error) {
	out, err := r.store.client.GetItem(ctx, &dynamodb.GetItemInput{
		TableName: aws.String(r.store.table),
		Key: map[string]types.AttributeValue{
			"PK": &types.AttributeValueMemberS{Value: userPK(userID)},
			"SK": &types.AttributeValueMemberS{Value: skPresence},
		},
	})
	if err != nil {
		return nil, err
	}
	if out.Item == nil {
		return nil, nil
	}
	var row presenceItem
	if err := attributevalue.UnmarshalMap(out.Item, &row); err != nil {
		return nil, err
	}
	return &domain.Presence{
		UserID:       userID,
		Status:       row.Status,
		LastActiveAt: row.LastActiveAt,
	}, nil
}

var _ output.PresenceRepository = (*PresenceRepository)(nil)
