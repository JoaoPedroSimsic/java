package ratelimit

import (
	"context"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
)

func setupTestRedis(t *testing.T) *redis.Client {
	client := redis.NewClient(&redis.Options{
		Addr: "localhost:6379",
	})

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	if err := client.Ping(ctx).Err(); err != nil {
		t.Skipf("Redis not available, skipping test: %v", err)
	}

	return client
}

func TestRedisRateLimiter_AllowsWithinLimit(t *testing.T) {
	redisClient := setupTestRedis(t)
	defer redisClient.Close()

	ctx := context.Background()
	key := "test:allow:" + time.Now().Format(time.RFC3339Nano)

	limiter := NewRedisRateLimiter(redisClient, time.Second)

	result, err := limiter.Check(ctx, key, 5)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if !result.Allowed {
		t.Error("expected request to be allowed")
	}
	if result.Limit != 5 {
		t.Errorf("expected limit to be 5, got %d", result.Limit)
	}
	if result.Remaining != 4 {
		t.Errorf("expected remaining to be 4, got %d", result.Remaining)
	}
}

func TestRedisRateLimiter_RejectsOverLimit(t *testing.T) {
	redisClient := setupTestRedis(t)
	defer redisClient.Close()

	ctx := context.Background()
	key := "test:reject:" + time.Now().Format(time.RFC3339Nano)

	limiter := NewRedisRateLimiter(redisClient, time.Second)

	for i := 0; i < 3; i++ {
		result, err := limiter.Check(ctx, key, 3)
		if err != nil {
			t.Fatalf("unexpected error on request %d: %v", i+1, err)
		}
		if !result.Allowed {
			t.Errorf("request %d should be allowed", i+1)
		}
	}

	result, err := limiter.Check(ctx, key, 3)
	if err != nil {
		t.Fatalf("unexpected error on over-limit request: %v", err)
	}

	if result.Allowed {
		t.Error("expected request to be rejected when over limit")
	}
	if result.Remaining != 0 {
		t.Errorf("expected remaining to be 0, got %d", result.Remaining)
	}
}

func TestRedisRateLimiter_WindowResets(t *testing.T) {
	redisClient := setupTestRedis(t)
	defer redisClient.Close()

	ctx := context.Background()
	key := "test:reset:" + time.Now().Format(time.RFC3339Nano)

	limiter := NewRedisRateLimiter(redisClient, 100*time.Millisecond)

	for i := 0; i < 3; i++ {
		_, _ = limiter.Check(ctx, key, 3)
	}

	result, _ := limiter.Check(ctx, key, 3)
	if result.Allowed {
		t.Error("expected request to be rejected before window reset")
	}

	time.Sleep(150 * time.Millisecond)

	result, err := limiter.Check(ctx, key, 3)
	if err != nil {
		t.Fatalf("unexpected error after window reset: %v", err)
	}

	if !result.Allowed {
		t.Error("expected request to be allowed after window reset")
	}
}

func TestRedisRateLimiter_ReturnsCorrectRemaining(t *testing.T) {
	redisClient := setupTestRedis(t)
	defer redisClient.Close()

	ctx := context.Background()
	key := "test:remaining:" + time.Now().Format(time.RFC3339Nano)

	limiter := NewRedisRateLimiter(redisClient, time.Second)

	limit := 5
	for i := 0; i < limit; i++ {
		result, err := limiter.Check(ctx, key, limit)
		if err != nil {
			t.Fatalf("unexpected error on request %d: %v", i+1, err)
		}

		expectedRemaining := limit - i - 1
		if result.Remaining != expectedRemaining {
			t.Errorf("request %d: expected remaining %d, got %d", i+1, expectedRemaining, result.Remaining)
		}
	}
}

func TestRedisRateLimiter_DifferentKeys(t *testing.T) {
	redisClient := setupTestRedis(t)
	defer redisClient.Close()

	ctx := context.Background()
	prefix := time.Now().Format(time.RFC3339Nano)
	key1 := "test:user1:" + prefix
	key2 := "test:user2:" + prefix

	limiter := NewRedisRateLimiter(redisClient, time.Second)

	for i := 0; i < 3; i++ {
		_, _ = limiter.Check(ctx, key1, 3)
	}

	result1, _ := limiter.Check(ctx, key1, 3)
	if result1.Allowed {
		t.Error("key1 should be rate limited")
	}

	result2, err := limiter.Check(ctx, key2, 3)
	if err != nil {
		t.Fatalf("unexpected error for key2: %v", err)
	}
	if !result2.Allowed {
		t.Error("key2 should be allowed (separate limit)")
	}
}

func TestRedisRateLimiter_ResetInDuration(t *testing.T) {
	redisClient := setupTestRedis(t)
	defer redisClient.Close()

	ctx := context.Background()
	key := "test:resetin:" + time.Now().Format(time.RFC3339Nano)

	window := 500 * time.Millisecond
	limiter := NewRedisRateLimiter(redisClient, window)

	result, err := limiter.Check(ctx, key, 5)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if result.ResetIn != window {
		t.Errorf("expected ResetIn to be %v, got %v", window, result.ResetIn)
	}
}

func TestRateLimitResult_Fields(t *testing.T) {
	result := &RateLimitResult{
		Allowed:   true,
		Limit:     10,
		Remaining: 5,
		ResetIn:   time.Second,
	}

	if !result.Allowed {
		t.Error("expected Allowed to be true")
	}
	if result.Limit != 10 {
		t.Errorf("expected Limit to be 10, got %d", result.Limit)
	}
	if result.Remaining != 5 {
		t.Errorf("expected Remaining to be 5, got %d", result.Remaining)
	}
	if result.ResetIn != time.Second {
		t.Errorf("expected ResetIn to be 1s, got %v", result.ResetIn)
	}
}
