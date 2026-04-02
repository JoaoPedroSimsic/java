package resilience

import (
	"context"
	"errors"
	"testing"
	"time"
)

func TestTimeLimiter_ExecuteSuccess(t *testing.T) {
	tl := NewTimeLimiter(TimeLimiterConfig{Timeout: 1 * time.Second})

	executed := false
	err := tl.Execute(context.Background(), func() error {
		executed = true
		return nil
	})

	if err != nil {
		t.Errorf("expected no error, got %v", err)
	}
	if !executed {
		t.Error("expected function to be executed")
	}
}

func TestTimeLimiter_ExecuteWithError(t *testing.T) {
	tl := NewTimeLimiter(TimeLimiterConfig{Timeout: 1 * time.Second})

	expectedErr := errors.New("test error")
	err := tl.Execute(context.Background(), func() error {
		return expectedErr
	})

	if !errors.Is(err, expectedErr) {
		t.Errorf("expected error %v, got %v", expectedErr, err)
	}
}

func TestTimeLimiter_ExecuteTimeout(t *testing.T) {
	tl := NewTimeLimiter(TimeLimiterConfig{Timeout: 50 * time.Millisecond})

	err := tl.Execute(context.Background(), func() error {
		time.Sleep(200 * time.Millisecond)
		return nil
	})

	if !errors.Is(err, ErrTimeout) {
		t.Errorf("expected ErrTimeout, got %v", err)
	}
}

func TestTimeLimiter_ExecuteContextCancelled(t *testing.T) {
	tl := NewTimeLimiter(TimeLimiterConfig{Timeout: 1 * time.Second})

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	err := tl.Execute(ctx, func() error {
		time.Sleep(100 * time.Millisecond)
		return nil
	})

	if err == nil {
		t.Error("expected error when context is cancelled")
	}
}

func TestExecuteWithResult_Success(t *testing.T) {
	tl := NewTimeLimiter(TimeLimiterConfig{Timeout: 1 * time.Second})

	result, err := ExecuteWithResult(tl, context.Background(), func() (string, error) {
		return "hello", nil
	})

	if err != nil {
		t.Errorf("expected no error, got %v", err)
	}
	if result != "hello" {
		t.Errorf("expected 'hello', got %s", result)
	}
}

func TestExecuteWithResult_Error(t *testing.T) {
	tl := NewTimeLimiter(TimeLimiterConfig{Timeout: 1 * time.Second})

	expectedErr := errors.New("test error")
	result, err := ExecuteWithResult(tl, context.Background(), func() (int, error) {
		return 0, expectedErr
	})

	if !errors.Is(err, expectedErr) {
		t.Errorf("expected error %v, got %v", expectedErr, err)
	}
	if result != 0 {
		t.Errorf("expected zero value, got %d", result)
	}
}

func TestExecuteWithResult_Timeout(t *testing.T) {
	tl := NewTimeLimiter(TimeLimiterConfig{Timeout: 50 * time.Millisecond})

	result, err := ExecuteWithResult(tl, context.Background(), func() (string, error) {
		time.Sleep(200 * time.Millisecond)
		return "should not return", nil
	})

	if !errors.Is(err, ErrTimeout) {
		t.Errorf("expected ErrTimeout, got %v", err)
	}
	if result != "" {
		t.Errorf("expected empty string, got %s", result)
	}
}

func TestDefaultTimeLimiterConfig(t *testing.T) {
	config := DefaultTimeLimiterConfig()

	if config.Timeout != 10*time.Second {
		t.Errorf("expected default timeout to be 10s, got %v", config.Timeout)
	}
}
