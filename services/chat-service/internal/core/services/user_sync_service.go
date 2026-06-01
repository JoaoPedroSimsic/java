package services

import (
	"context"

	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/input"
	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/output"
	"go.uber.org/zap"
)

type UserSyncService struct {
	users  output.UserRepository
	logger *zap.Logger
}

func NewUserSyncService(users output.UserRepository, logger *zap.Logger) *UserSyncService {
	return &UserSyncService{users: users, logger: logger}
}

func (s *UserSyncService) HandleUserEvent(ctx context.Context, cmd input.UserEventCommand) error {
	s.logger.Debug("HandleUserEvent not implemented",
		zap.String("aggregateId", cmd.AggregateID),
		zap.String("eventType", cmd.EventType),
	)
	return nil
}

var _ input.UserSyncUseCase = (*UserSyncService)(nil)
