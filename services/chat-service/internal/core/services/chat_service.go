package services

import (
	"context"

	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/input"
	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/output"
	"github.com/joaosimsic/hermes/chat-service/internal/protocol"
	"go.uber.org/zap"
)

type ChatService struct {
	conversations output.ConversationRepository
	messages      output.MessageRepository
	readReceipts  output.ReadReceiptRepository
	presence      output.PresenceRepository
	users         output.UserRepository
	publisher     output.MessagePublisher
	logger        *zap.Logger
}

func NewChatService(
	conversations output.ConversationRepository,
	messages output.MessageRepository,
	readReceipts output.ReadReceiptRepository,
	presence output.PresenceRepository,
	users output.UserRepository,
	publisher output.MessagePublisher,
	logger *zap.Logger,
) *ChatService {
	return &ChatService{
		conversations: conversations,
		messages:      messages,
		readReceipts:  readReceipts,
		presence:      presence,
		users:         users,
		publisher:     publisher,
		logger:        logger,
	}
}

func (s *ChatService) SendMessage(ctx context.Context, cmd input.SendMessageCommand) error {
	s.logger.Debug("SendMessage not implemented", zap.String("conversationId", cmd.ConversationID))
	return nil
}

func (s *ChatService) MarkRead(ctx context.Context, cmd input.MarkReadCommand) error {
	s.logger.Debug("MarkRead not implemented", zap.String("conversationId", cmd.ConversationID))
	return nil
}

func (s *ChatService) HandleTyping(ctx context.Context, cmd input.TypingCommand) error {
	s.logger.Debug("HandleTyping not implemented", zap.String("conversationId", cmd.ConversationID))
	return nil
}

func (s *ChatService) HandlePresence(ctx context.Context, cmd input.PresenceCommand) error {
	s.logger.Debug("HandlePresence not implemented", zap.String("userId", cmd.UserID))
	return nil
}

func (s *ChatService) CreateConversation(ctx context.Context, userID string, req protocol.CreateConversationRequest) (*protocol.ConversationResponse, bool, error) {
	s.logger.Debug("CreateConversation not implemented", zap.String("userId", userID))
	return nil, false, nil
}

func (s *ChatService) ListConversations(ctx context.Context, userID string) (*protocol.InboxResponse, error) {
	s.logger.Debug("ListConversations not implemented", zap.String("userId", userID))
	return &protocol.InboxResponse{Conversations: []protocol.InboxConversationResponse{}}, nil
}

func (s *ChatService) ListMessages(ctx context.Context, userID, conversationID, cursor string, limit int) (*protocol.MessageHistoryResponse, error) {
	s.logger.Debug("ListMessages not implemented",
		zap.String("userId", userID),
		zap.String("conversationId", conversationID),
	)
	return &protocol.MessageHistoryResponse{Messages: []protocol.MessageHistoryItem{}}, nil
}

var _ input.ChatUseCase = (*ChatService)(nil)
