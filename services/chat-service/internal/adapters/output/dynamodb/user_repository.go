package dynamodb

import (
	"context"
	"errors"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
	"github.com/joaosimsic/hermes/chat-service/internal/core/domain"
	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/output"
)

type UserRepository struct {
	store *Store
}

func NewUserRepository(store *Store) *UserRepository {
	return &UserRepository{store: store}
}

func (r *UserRepository) UpsertProfile(ctx context.Context, profile domain.UserProfile) error {
	item := profileItem{
		PK:        userPK(profile.UserID),
		SK:        skProfile,
		Email:     profile.Email,
		Name:      profile.Name,
		UpdatedAt: profile.UpdatedAt,
	}
	if item.UpdatedAt.IsZero() {
		item.UpdatedAt = time.Now().UTC()
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

func (r *UserRepository) DeleteProfile(ctx context.Context, userID string) error {
	_, err := r.store.client.DeleteItem(ctx, &dynamodb.DeleteItemInput{
		TableName: aws.String(r.store.table),
		Key: map[string]types.AttributeValue{
			"PK": &types.AttributeValueMemberS{Value: userPK(userID)},
			"SK": &types.AttributeValueMemberS{Value: skProfile},
		},
	})
	return err
}

func (r *UserRepository) GetProfile(ctx context.Context, userID string) (*domain.UserProfile, error) {
	out, err := r.store.client.GetItem(ctx, &dynamodb.GetItemInput{
		TableName: aws.String(r.store.table),
		Key: map[string]types.AttributeValue{
			"PK": &types.AttributeValueMemberS{Value: userPK(userID)},
			"SK": &types.AttributeValueMemberS{Value: skProfile},
		},
	})
	if err != nil {
		return nil, err
	}
	if out.Item == nil {
		return nil, nil
	}
	var row profileItem
	if err := attributevalue.UnmarshalMap(out.Item, &row); err != nil {
		return nil, err
	}
	return &domain.UserProfile{
		UserID:    userID,
		Email:     row.Email,
		Name:      row.Name,
		UpdatedAt: row.UpdatedAt,
	}, nil
}

func (r *UserRepository) RecordEvent(ctx context.Context, aggregateID, occurredAt string) (bool, error) {
	item := eventItem{PK: eventPK(aggregateID, occurredAt), SK: skEvent}
	av, err := attributevalue.MarshalMap(item)
	if err != nil {
		return false, err
	}
	_, err = r.store.client.PutItem(ctx, &dynamodb.PutItemInput{
		TableName:           aws.String(r.store.table),
		Item:                av,
		ConditionExpression: aws.String("attribute_not_exists(PK)"),
	})
	if err != nil {
		var cond *types.ConditionalCheckFailedException
		if errors.As(err, &cond) {
			return false, nil
		}
		return false, err
	}
	return true, nil
}

var _ output.UserRepository = (*UserRepository)(nil)
