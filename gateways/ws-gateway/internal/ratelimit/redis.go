package ratelimit

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
)

type RateLimitResult struct {
	Allowed   bool
	Limit     int
	Remaining int
	ResetIn   time.Duration
}

type RedisRateLimiter struct {
	client *redis.Client
	window time.Duration
}

func NewRedisRateLimiter(client *redis.Client, window time.Duration) *RedisRateLimiter {
	return &RedisRateLimiter{
		client: client,
		window: window,
	}
}

func (r *RedisRateLimiter) Check(ctx context.Context, key string, limit int) (*RateLimitResult, error) {
	now := time.Now().UnixMilli()
	windowStart := now - r.window.Milliseconds()
	redisKey := fmt.Sprintf("rate_limit:%s", key)

	pipe := r.client.Pipeline()

	pipe.ZRemRangeByScore(ctx, redisKey, "0", fmt.Sprintf("%d", windowStart))

	countCmd := pipe.ZCard(ctx, redisKey)

	_, err := pipe.Exec(ctx)
	if err != nil {
		return &RateLimitResult{Allowed: true, Limit: limit, Remaining: limit, ResetIn: r.window}, nil
	}

	currentCount := countCmd.Val()

	if currentCount >= int64(limit) {
		return &RateLimitResult{
			Allowed:   false,
			Limit:     limit,
			Remaining: 0,
			ResetIn:   r.window,
		}, nil
	}

	member := fmt.Sprintf("%d:%s", now, uuid.New().String()[:8])

	pipe = r.client.Pipeline()
	pipe.ZAdd(ctx, redisKey, redis.Z{Score: float64(now), Member: member})
	pipe.Expire(ctx, redisKey, r.window*2)

	if _, err := pipe.Exec(ctx); err != nil {
		return &RateLimitResult{Allowed: true, Limit: limit, Remaining: limit, ResetIn: r.window}, nil
	}

	remaining := limit - int(currentCount) - 1

	remaining = max(0, remaining)

	return &RateLimitResult{
		Allowed:   true,
		Limit:     limit,
		Remaining: remaining,
		ResetIn:   r.window,
	}, nil
}

func (r *RedisRateLimiter) Close() error {
	return r.client.Close()
}
