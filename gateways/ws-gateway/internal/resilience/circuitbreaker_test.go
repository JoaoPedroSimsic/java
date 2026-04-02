package resilience

import (
	"sync"
	"testing"
	"time"
)

func TestCircuitBreaker_InitialState(t *testing.T) {
	cb := NewCircuitBreaker("test", DefaultCircuitBreakerConfig())

	if cb.State() != StateClosed {
		t.Errorf("expected initial state to be Closed, got %s", cb.State())
	}

	if cb.Name() != "test" {
		t.Errorf("expected name to be 'test', got %s", cb.Name())
	}
}

func TestCircuitBreaker_AllowsRequestsWhenClosed(t *testing.T) {
	cb := NewCircuitBreaker("test", DefaultCircuitBreakerConfig())

	for i := 0; i < 10; i++ {
		if err := cb.Allow(); err != nil {
			t.Errorf("expected Allow() to succeed when closed, got %v", err)
		}
	}
}

func TestCircuitBreaker_TransitionsToOpenOnFailureThreshold(t *testing.T) {
	config := CircuitBreakerConfig{
		SlidingWindowSize:                            5,
		MinimumNumberOfCalls:                         3,
		FailureRateThreshold:                         50.0,
		WaitDurationInOpenState:                      1 * time.Second,
		PermittedNumberOfCallsInHalfOpenState:        2,
		AutomaticTransitionFromOpenToHalfOpenEnabled: true,
	}

	cb := NewCircuitBreaker("test", config)

	cb.RecordSuccess()
	cb.RecordFailure()
	cb.RecordFailure()

	if cb.State() != StateOpen {
		t.Errorf("expected state to be Open after 66%% failure rate, got %s", cb.State())
	}
}

func TestCircuitBreaker_RejectsRequestsWhenOpen(t *testing.T) {
	config := CircuitBreakerConfig{
		SlidingWindowSize:                            5,
		MinimumNumberOfCalls:                         2,
		FailureRateThreshold:                         50.0,
		WaitDurationInOpenState:                      1 * time.Hour,
		PermittedNumberOfCallsInHalfOpenState:        2,
		AutomaticTransitionFromOpenToHalfOpenEnabled: false,
	}

	cb := NewCircuitBreaker("test", config)

	cb.RecordFailure()
	cb.RecordFailure()

	err := cb.Allow()
	if err != ErrCircuitOpen {
		t.Errorf("expected ErrCircuitOpen, got %v", err)
	}
}

func TestCircuitBreaker_TransitionsToHalfOpenAfterWaitDuration(t *testing.T) {
	config := CircuitBreakerConfig{
		SlidingWindowSize:                            5,
		MinimumNumberOfCalls:                         2,
		FailureRateThreshold:                         50.0,
		WaitDurationInOpenState:                      50 * time.Millisecond,
		PermittedNumberOfCallsInHalfOpenState:        2,
		AutomaticTransitionFromOpenToHalfOpenEnabled: true,
	}

	cb := NewCircuitBreaker("test", config)

	cb.RecordFailure()
	cb.RecordFailure()

	if cb.State() != StateOpen {
		t.Fatalf("expected state to be Open, got %s", cb.State())
	}

	time.Sleep(60 * time.Millisecond)

	err := cb.Allow()
	if err != nil {
		t.Errorf("expected Allow() to succeed in half-open state, got %v", err)
	}

	if cb.State() != StateHalfOpen {
		t.Errorf("expected state to be HalfOpen, got %s", cb.State())
	}
}

func TestCircuitBreaker_TransitionsToClosedOnSuccessInHalfOpen(t *testing.T) {
	config := CircuitBreakerConfig{
		SlidingWindowSize:                            5,
		MinimumNumberOfCalls:                         2,
		FailureRateThreshold:                         50.0,
		WaitDurationInOpenState:                      10 * time.Millisecond,
		PermittedNumberOfCallsInHalfOpenState:        2,
		AutomaticTransitionFromOpenToHalfOpenEnabled: true,
	}

	cb := NewCircuitBreaker("test", config)

	cb.RecordFailure()
	cb.RecordFailure()

	time.Sleep(20 * time.Millisecond)

	_ = cb.Allow()
	cb.RecordSuccess()
	_ = cb.Allow()
	cb.RecordSuccess()

	if cb.State() != StateClosed {
		t.Errorf("expected state to be Closed after successful half-open calls, got %s", cb.State())
	}
}

func TestCircuitBreaker_TransitionsBackToOpenOnFailureInHalfOpen(t *testing.T) {
	config := CircuitBreakerConfig{
		SlidingWindowSize:                            5,
		MinimumNumberOfCalls:                         2,
		FailureRateThreshold:                         50.0,
		WaitDurationInOpenState:                      10 * time.Millisecond,
		PermittedNumberOfCallsInHalfOpenState:        3,
		AutomaticTransitionFromOpenToHalfOpenEnabled: true,
	}

	cb := NewCircuitBreaker("test", config)

	cb.RecordFailure()
	cb.RecordFailure()

	time.Sleep(20 * time.Millisecond)

	_ = cb.Allow()
	cb.RecordFailure()

	if cb.State() != StateOpen {
		t.Errorf("expected state to be Open after failure in half-open, got %s", cb.State())
	}
}

func TestCircuitBreaker_LimitsCallsInHalfOpen(t *testing.T) {
	config := CircuitBreakerConfig{
		SlidingWindowSize:                            5,
		MinimumNumberOfCalls:                         2,
		FailureRateThreshold:                         50.0,
		WaitDurationInOpenState:                      10 * time.Millisecond,
		PermittedNumberOfCallsInHalfOpenState:        2,
		AutomaticTransitionFromOpenToHalfOpenEnabled: true,
	}

	cb := NewCircuitBreaker("test", config)

	cb.RecordFailure()
	cb.RecordFailure()

	time.Sleep(20 * time.Millisecond)

	if err := cb.Allow(); err != nil {
		t.Errorf("first half-open call should be allowed: %v", err)
	}
	if err := cb.Allow(); err != nil {
		t.Errorf("second half-open call should be allowed: %v", err)
	}
	if err := cb.Allow(); err != ErrCircuitHalfOpen {
		t.Errorf("third half-open call should be rejected: got %v", err)
	}
}

func TestCircuitBreaker_Reset(t *testing.T) {
	config := DefaultCircuitBreakerConfig()
	config.MinimumNumberOfCalls = 2

	cb := NewCircuitBreaker("test", config)

	cb.RecordFailure()
	cb.RecordFailure()

	if cb.State() != StateOpen {
		t.Fatalf("expected state to be Open, got %s", cb.State())
	}

	cb.Reset()

	if cb.State() != StateClosed {
		t.Errorf("expected state to be Closed after reset, got %s", cb.State())
	}

	if err := cb.Allow(); err != nil {
		t.Errorf("expected Allow() to succeed after reset, got %v", err)
	}
}

func TestCircuitBreaker_Metrics(t *testing.T) {
	config := DefaultCircuitBreakerConfig()
	cb := NewCircuitBreaker("test", config)

	cb.RecordSuccess()
	cb.RecordSuccess()
	cb.RecordFailure()

	state, failures, total, failureRate := cb.Metrics()

	if state != StateClosed {
		t.Errorf("expected state Closed, got %s", state)
	}
	if failures != 1 {
		t.Errorf("expected 1 failure, got %d", failures)
	}
	if total != 3 {
		t.Errorf("expected 3 total calls, got %d", total)
	}
	expectedRate := (1.0 / 3.0) * 100
	if failureRate < expectedRate-0.1 || failureRate > expectedRate+0.1 {
		t.Errorf("expected failure rate ~%.2f, got %.2f", expectedRate, failureRate)
	}
}

func TestCircuitBreaker_StateChangeNotifier(t *testing.T) {
	config := CircuitBreakerConfig{
		SlidingWindowSize:                            5,
		MinimumNumberOfCalls:                         2,
		FailureRateThreshold:                         50.0,
		WaitDurationInOpenState:                      1 * time.Hour,
		PermittedNumberOfCallsInHalfOpenState:        2,
		AutomaticTransitionFromOpenToHalfOpenEnabled: false,
	}

	cb := NewCircuitBreaker("test", config)

	var mu sync.Mutex
	var transitions []struct {
		name string
		from State
		to   State
	}

	cb.SetStateChangeNotifier(func(name string, from, to State) {
		mu.Lock()
		defer mu.Unlock()
		transitions = append(transitions, struct {
			name string
			from State
			to   State
		}{name, from, to})
	})

	cb.RecordFailure()
	cb.RecordFailure()

	time.Sleep(10 * time.Millisecond)

	mu.Lock()
	defer mu.Unlock()

	if len(transitions) != 1 {
		t.Fatalf("expected 1 transition, got %d", len(transitions))
	}

	if transitions[0].from != StateClosed || transitions[0].to != StateOpen {
		t.Errorf("expected transition Closed -> Open, got %s -> %s",
			transitions[0].from, transitions[0].to)
	}
}

func TestCircuitBreaker_SlidingWindow(t *testing.T) {
	config := CircuitBreakerConfig{
		SlidingWindowSize:                            3,
		MinimumNumberOfCalls:                         3,
		FailureRateThreshold:                         60.0,
		WaitDurationInOpenState:                      1 * time.Hour,
		PermittedNumberOfCallsInHalfOpenState:        2,
		AutomaticTransitionFromOpenToHalfOpenEnabled: false,
	}

	cb := NewCircuitBreaker("test", config)

	cb.RecordFailure()
	cb.RecordFailure()
	cb.RecordSuccess()

	if cb.State() != StateOpen {
		t.Fatalf("expected state to be Open with 66%% failure rate (threshold 60%%), got %s", cb.State())
	}
}

func TestCircuitBreaker_ConcurrentAccess(t *testing.T) {
	cb := NewCircuitBreaker("test", DefaultCircuitBreakerConfig())

	var wg sync.WaitGroup
	for i := 0; i < 100; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_ = cb.Allow()
			if i%2 == 0 {
				cb.RecordSuccess()
			} else {
				cb.RecordFailure()
			}
		}()
	}
	wg.Wait()

	_ = cb.State()
	_, _, _, _ = cb.Metrics()
}

func TestState_String(t *testing.T) {
	tests := []struct {
		state    State
		expected string
	}{
		{StateClosed, "closed"},
		{StateOpen, "open"},
		{StateHalfOpen, "half-open"},
		{State(99), "unknown"},
	}

	for _, tt := range tests {
		if got := tt.state.String(); got != tt.expected {
			t.Errorf("State(%d).String() = %s, want %s", tt.state, got, tt.expected)
		}
	}
}
