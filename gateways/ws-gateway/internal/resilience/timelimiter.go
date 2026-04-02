package resilience

import (
	"context"
	"errors"
	"time"
)

var ErrTimeout = errors.New("operation timed out")

type TimeLimiterConfig struct {
	Timeout time.Duration
}

func DefaultTimeLimiterConfig() TimeLimiterConfig {
	return TimeLimiterConfig{
		Timeout: 10 * time.Second,
	}
}

type TimeLimiter struct {
	config TimeLimiterConfig
}

func NewTimeLimiter(config TimeLimiterConfig) *TimeLimiter {
	return &TimeLimiter{config: config}
}

func (tl *TimeLimiter) Execute(ctx context.Context, fn func() error) error {
	ctx, cancel := context.WithTimeout(ctx, tl.config.Timeout)
	defer cancel()

	done := make(chan error, 1)

	go func() {
		done <- fn()
	}()

	select {
	case err := <-done:
		return err
	case <-ctx.Done():
		if errors.Is(ctx.Err(), context.DeadlineExceeded) {
			return ErrTimeout
		}
		return ctx.Err()
	}
}

func ExecuteWithResult[T any](tl *TimeLimiter, ctx context.Context, fn func() (T, error)) (T, error) {
	ctx, cancel := context.WithTimeout(ctx, tl.config.Timeout)
	defer cancel()

	type result struct {
		value T
		err   error
	}

	done := make(chan result, 1)

	go func() {
		v, err := fn()
		done <- result{value: v, err: err}
	}()

	select {
	case res := <-done:
		return res.value, res.err
	case <-ctx.Done():
		var zero T
		if errors.Is(ctx.Err(), context.DeadlineExceeded) {
			return zero, ErrTimeout
		}
		return zero, ctx.Err()
	}
}
