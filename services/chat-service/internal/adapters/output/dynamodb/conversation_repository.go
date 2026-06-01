package dynamodb

import (
	"context"
	"errors"
	"fmt"
	"sort"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
	"github.com/joaosimsic/hermes/chat-service/internal/core/domain"
	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/output"
	"github.com/oklog/ulid/v2"
)

type ConversationRepository struct {
	store *Store
}

func NewConversationRepository(store *Store) *ConversationRepository {
	return &ConversationRepository{store: store}
}

func (r *ConversationRepository) GetOrCreateDirect(ctx context.Context, userA, userB string) (*domain.Conversation, bool, error) {
	convID := DirectConversationID(userA, userB)
	existing, err := r.GetMeta(ctx, convID)
	if err == nil {
		return existing, false, nil
	}

	now := time.Now().UTC()
	members := []string{userA, userB}
	meta := conversationMetaItem{
		PK:        convPK(convID),
		SK:        skMeta,
		Type:      string(domain.ConversationDirect),
		CreatedBy: userA,
		Members:   members,
		CreatedAt: now,
	}
	metaAV, err := attributevalue.MarshalMap(meta)
	if err != nil {
		return nil, false, err
	}

	inboxA, _ := attributevalue.MarshalMap(inboxItem{
		PK: userPK(userA), SK: inboxSK(convID), UnreadCount: 0,
	})
	inboxB, _ := attributevalue.MarshalMap(inboxItem{
		PK: userPK(userB), SK: inboxSK(convID), UnreadCount: 0,
	})

	_, err = r.store.client.TransactWriteItems(ctx, &dynamodb.TransactWriteItemsInput{
		TransactItems: []types.TransactWriteItem{
			{
				Put: &types.Put{
					TableName:           aws.String(r.store.table),
					Item:                metaAV,
					ConditionExpression: aws.String("attribute_not_exists(PK)"),
				},
			},
			{Put: &types.Put{TableName: aws.String(r.store.table), Item: inboxA}},
			{Put: &types.Put{TableName: aws.String(r.store.table), Item: inboxB}},
		},
	})
	if err != nil {
		var txErr *types.TransactionCanceledException
		if errors.As(err, &txErr) {
			conv, getErr := r.GetMeta(ctx, convID)
			return conv, false, getErr
		}
		return nil, false, err
	}

	conv := metaToDomain(meta)
	return &conv, true, nil
}

func (r *ConversationRepository) CreateGroup(ctx context.Context, creatorID, name string, memberIDs []string) (*domain.Conversation, error) {
	members := uniqueStrings(append(memberIDs, creatorID))
	convID := ulid.Make().String()
	now := time.Now().UTC()

	meta := conversationMetaItem{
		PK:        convPK(convID),
		SK:        skMeta,
		Type:      string(domain.ConversationGroup),
		Name:      name,
		CreatedBy: creatorID,
		Members:   members,
		CreatedAt: now,
	}
	metaAV, err := attributevalue.MarshalMap(meta)
	if err != nil {
		return nil, err
	}

	items := []types.TransactWriteItem{{Put: &types.Put{TableName: aws.String(r.store.table), Item: metaAV}}}
	for _, memberID := range members {
		inboxAV, err := attributevalue.MarshalMap(inboxItem{
			PK: userPK(memberID), SK: inboxSK(convID), UnreadCount: 0,
		})
		if err != nil {
			return nil, err
		}
		items = append(items, types.TransactWriteItem{Put: &types.Put{TableName: aws.String(r.store.table), Item: inboxAV}})
	}
	if len(items) > 25 {
		return nil, fmt.Errorf("group exceeds transact item limit")
	}

	if _, err := r.store.client.TransactWriteItems(ctx, &dynamodb.TransactWriteItemsInput{TransactItems: items}); err != nil {
		return nil, err
	}
	conv := metaToDomain(meta)
	return &conv, nil
}

func (r *ConversationRepository) GetMeta(ctx context.Context, conversationID string) (*domain.Conversation, error) {
	out, err := r.store.client.GetItem(ctx, &dynamodb.GetItemInput{
		TableName: aws.String(r.store.table),
		Key: map[string]types.AttributeValue{
			"PK": &types.AttributeValueMemberS{Value: convPK(conversationID)},
			"SK": &types.AttributeValueMemberS{Value: skMeta},
		},
	})
	if err != nil {
		return nil, err
	}
	if out.Item == nil {
		return nil, fmt.Errorf("conversation not found: %s", conversationID)
	}
	var meta conversationMetaItem
	if err := attributevalue.UnmarshalMap(out.Item, &meta); err != nil {
		return nil, err
	}
	conv := metaToDomain(meta)
	return &conv, nil
}

func (r *ConversationRepository) IsMember(ctx context.Context, userID, conversationID string) (bool, error) {
	out, err := r.store.client.GetItem(ctx, &dynamodb.GetItemInput{
		TableName: aws.String(r.store.table),
		Key: map[string]types.AttributeValue{
			"PK": &types.AttributeValueMemberS{Value: userPK(userID)},
			"SK": &types.AttributeValueMemberS{Value: inboxSK(conversationID)},
		},
	})
	if err != nil {
		return false, err
	}
	return out.Item != nil, nil
}

func (r *ConversationRepository) ListInbox(ctx context.Context, userID string) ([]domain.InboxEntry, error) {
	out, err := r.store.client.Query(ctx, &dynamodb.QueryInput{
		TableName:              aws.String(r.store.table),
		KeyConditionExpression: aws.String("PK = :pk AND begins_with(SK, :sk)"),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":pk": &types.AttributeValueMemberS{Value: userPK(userID)},
			":sk": &types.AttributeValueMemberS{Value: "CONV#"},
		},
	})
	if err != nil {
		return nil, err
	}

	entries := make([]domain.InboxEntry, 0, len(out.Items))
	for _, item := range out.Items {
		var row inboxItem
		if err := attributevalue.UnmarshalMap(item, &row); err != nil {
			return nil, err
		}
		convID := row.SK[len("CONV#"):]
		var lastAt *time.Time
		if !row.LastMessageAt.IsZero() {
			t := row.LastMessageAt
			lastAt = &t
		}
		entries = append(entries, domain.InboxEntry{
			ConversationID:    convID,
			LastMessageAt:     lastAt,
			UnreadCount:       row.UnreadCount,
			LastReadMessageID: row.LastReadMessageID,
		})
	}
	sortInboxByLastMessage(entries)
	return entries, nil
}

func sortInboxByLastMessage(entries []domain.InboxEntry) {
	sort.Slice(entries, func(i, j int) bool {
		ti, tj := entries[i].LastMessageAt, entries[j].LastMessageAt
		if ti == nil {
			return false
		}
		if tj == nil {
			return true
		}
		return ti.After(*tj)
	})
}

func (r *ConversationRepository) BumpConversationMeta(ctx context.Context, conversationID string, lastMessage domain.Message) error {
	_, err := r.store.client.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName: aws.String(r.store.table),
		Key: map[string]types.AttributeValue{
			"PK": &types.AttributeValueMemberS{Value: convPK(conversationID)},
			"SK": &types.AttributeValueMemberS{Value: skMeta},
		},
		UpdateExpression: aws.String("SET lastMessageAt = :at, preview = :preview"),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":at":      &types.AttributeValueMemberS{Value: lastMessage.CreatedAt.Format(time.RFC3339Nano)},
			":preview": &types.AttributeValueMemberS{Value: truncatePreview(lastMessage.Content)},
		},
	})
	return err
}

func (r *ConversationRepository) IncrementUnread(ctx context.Context, conversationID string, memberIDs []string, excludeUserID string) error {
	for _, memberID := range memberIDs {
		if memberID == excludeUserID {
			continue
		}
		if _, err := r.store.client.UpdateItem(ctx, &dynamodb.UpdateItemInput{
			TableName: aws.String(r.store.table),
			Key: map[string]types.AttributeValue{
				"PK": &types.AttributeValueMemberS{Value: userPK(memberID)},
				"SK": &types.AttributeValueMemberS{Value: inboxSK(conversationID)},
			},
			UpdateExpression: aws.String("ADD unreadCount :one SET lastMessageAt = :at"),
			ExpressionAttributeValues: map[string]types.AttributeValue{
				":one": &types.AttributeValueMemberN{Value: "1"},
				":at":  &types.AttributeValueMemberS{Value: time.Now().UTC().Format(time.RFC3339Nano)},
			},
		}); err != nil {
			return err
		}
	}
	return nil
}

func (r *ConversationRepository) ResetUnread(ctx context.Context, userID, conversationID, lastReadMessageID string) error {
	_, err := r.store.client.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName: aws.String(r.store.table),
		Key: map[string]types.AttributeValue{
			"PK": &types.AttributeValueMemberS{Value: userPK(userID)},
			"SK": &types.AttributeValueMemberS{Value: inboxSK(conversationID)},
		},
		UpdateExpression: aws.String("SET unreadCount = :zero, lastReadMessageId = :msg"),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":zero": &types.AttributeValueMemberN{Value: "0"},
			":msg":  &types.AttributeValueMemberS{Value: lastReadMessageID},
		},
	})
	return err
}

func metaToDomain(meta conversationMetaItem) domain.Conversation {
	var lastAt *time.Time
	if !meta.LastMessageAt.IsZero() {
		t := meta.LastMessageAt
		lastAt = &t
	}
	return domain.Conversation{
		ID:            meta.PK[len("CONV#"):],
		Type:          domain.ConversationType(meta.Type),
		Name:          meta.Name,
		CreatedBy:     meta.CreatedBy,
		Members:       meta.Members,
		CreatedAt:     meta.CreatedAt,
		LastMessageAt: lastAt,
		Preview:       meta.Preview,
	}
}

func truncatePreview(content string) string {
	const max = 120
	if len(content) <= max {
		return content
	}
	return content[:max]
}

func uniqueStrings(ids []string) []string {
	seen := make(map[string]struct{}, len(ids))
	out := make([]string, 0, len(ids))
	for _, id := range ids {
		if id == "" {
			continue
		}
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		out = append(out, id)
	}
	return out
}

var _ output.ConversationRepository = (*ConversationRepository)(nil)
