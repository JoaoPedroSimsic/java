package dynamodb

import (
	"context"
	"errors"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
	"github.com/joaosimsic/hermes/chat-service/internal/core/domain"
	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/output"
)

type MessageRepository struct {
	store *Store
}

func NewMessageRepository(store *Store) *MessageRepository {
	return &MessageRepository{store: store}
}

func (r *MessageRepository) PutMessage(ctx context.Context, msg domain.Message) error {
	msgItem := messageItem{
		PK:          convPK(msg.ConversationID),
		SK:          msgSK(msg.MessageID),
		MessageID:   msg.MessageID,
		SenderID:    msg.SenderID,
		Content:     msg.Content,
		MediaID:     msg.MediaID,
		ClientMsgID: msg.ClientMsgID,
		CreatedAt:   msg.CreatedAt,
	}
	dedup := dedupItem{
		PK:        convPK(msg.ConversationID),
		SK:        dedupSK(msg.ClientMsgID),
		MessageID: msg.MessageID,
	}

	msgAV, err := attributevalue.MarshalMap(msgItem)
	if err != nil {
		return err
	}
	dedupAV, err := attributevalue.MarshalMap(dedup)
	if err != nil {
		return err
	}

	_, err = r.store.client.TransactWriteItems(ctx, &dynamodb.TransactWriteItemsInput{
		TransactItems: []types.TransactWriteItem{
			{Put: &types.Put{TableName: aws.String(r.store.table), Item: msgAV}},
			{
				Put: &types.Put{
					TableName:           aws.String(r.store.table),
					Item:                dedupAV,
					ConditionExpression: aws.String("attribute_not_exists(SK)"),
				},
			},
		},
	})
	return err
}

func (r *MessageRepository) GetDedupMessageID(ctx context.Context, conversationID, clientMsgID string) (string, bool, error) {
	out, err := r.store.client.GetItem(ctx, &dynamodb.GetItemInput{
		TableName: aws.String(r.store.table),
		Key: map[string]types.AttributeValue{
			"PK": &types.AttributeValueMemberS{Value: convPK(conversationID)},
			"SK": &types.AttributeValueMemberS{Value: dedupSK(clientMsgID)},
		},
	})
	if err != nil {
		return "", false, err
	}
	if out.Item == nil {
		return "", false, nil
	}
	var item dedupItem
	if err := attributevalue.UnmarshalMap(out.Item, &item); err != nil {
		return "", false, err
	}
	return item.MessageID, true, nil
}

func (r *MessageRepository) ListMessages(ctx context.Context, conversationID, cursor string, limit int) ([]domain.Message, string, error) {
	if limit <= 0 {
		limit = 50
	}
	startKey, err := decodeCursor(cursor)
	if err != nil {
		return nil, "", err
	}

	out, err := r.store.client.Query(ctx, &dynamodb.QueryInput{
		TableName:              aws.String(r.store.table),
		KeyConditionExpression: aws.String("PK = :pk AND begins_with(SK, :sk)"),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":pk": &types.AttributeValueMemberS{Value: convPK(conversationID)},
			":sk": &types.AttributeValueMemberS{Value: "MSG#"},
		},
		ScanIndexForward:     aws.Bool(false),
		Limit:                aws.Int32(int32(limit)),
		ExclusiveStartKey:    startKey,
	})
	if err != nil {
		return nil, "", err
	}

	messages := make([]domain.Message, 0, len(out.Items))
	for _, item := range out.Items {
		var row messageItem
		if err := attributevalue.UnmarshalMap(item, &row); err != nil {
			return nil, "", err
		}
		messages = append(messages, domain.Message{
			ConversationID: conversationID,
			MessageID:      row.MessageID,
			SenderID:       row.SenderID,
			Content:        row.Content,
			MediaID:        row.MediaID,
			ClientMsgID:    row.ClientMsgID,
			CreatedAt:      row.CreatedAt,
		})
	}

	next, err := encodeCursor(out.LastEvaluatedKey)
	if err != nil {
		return nil, "", err
	}
	return messages, next, nil
}

func IsDuplicateMessage(err error) bool {
	var txErr *types.TransactionCanceledException
	if !errors.As(err, &txErr) {
		return false
	}
	for _, reason := range txErr.CancellationReasons {
		if reason.Code != nil && *reason.Code == "ConditionalCheckFailed" {
			return true
		}
	}
	return false
}

// Ensure MessageRepository implements the port at compile time.
var _ output.MessageRepository = (*MessageRepository)(nil)
