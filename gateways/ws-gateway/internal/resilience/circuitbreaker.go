package resilience

import (
	"errors"
	"sync"
	"time"
)

type State int

const (
	StateClosed State = iota
	StateOpen
	StateHalfOpen
)

func (s State) String() string {
	switch s {
	case StateClosed:
		return "closed"
	case StateOpen:
		return "open"
	case StateHalfOpen:
		return "half-open"
	default:
		return "unknown"
	}
}

var (
	ErrCircuitOpen     = errors.New("circuit breaker is open")
	ErrCircuitHalfOpen = errors.New("circuit breaker is half-open, limited requests allowed")
)

type CircuitBreakerConfig struct {
	SlidingWindowSize                            int
	MinimumNumberOfCalls                         int
	FailureRateThreshold                         float64
	WaitDurationInOpenState                      time.Duration
	PermittedNumberOfCallsInHalfOpenState        int
	AutomaticTransitionFromOpenToHalfOpenEnabled bool
}

func DefaultCircuitBreakerConfig() CircuitBreakerConfig {
	return CircuitBreakerConfig{
		SlidingWindowSize:                            10,
		MinimumNumberOfCalls:                         5,
		FailureRateThreshold:                         50.0,
		WaitDurationInOpenState:                      30 * time.Second,
		PermittedNumberOfCallsInHalfOpenState:        3,
		AutomaticTransitionFromOpenToHalfOpenEnabled: true,
	}
}

type CircuitBreaker struct {
	name   string
	config CircuitBreakerConfig

	mu                sync.RWMutex
	state             State
	failures          int
	successes         int
	totalCalls        int
	halfOpenCalls     int
	lastStateChange   time.Time
	callResults       []bool
	stateChangeNotify func(name string, from, to State)
}

func NewCircuitBreaker(name string, config CircuitBreakerConfig) *CircuitBreaker {
	return &CircuitBreaker{
		name:            name,
		config:          config,
		state:           StateClosed,
		callResults:     make([]bool, 0, config.SlidingWindowSize),
		lastStateChange: time.Now(),
	}
}

func (cb *CircuitBreaker) SetStateChangeNotifier(fn func(name string, from, to State)) {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	cb.stateChangeNotify = fn
}

func (cb *CircuitBreaker) Name() string {
	return cb.name
}

func (cb *CircuitBreaker) State() State {
	cb.mu.RLock()
	defer cb.mu.RUnlock()
	return cb.state
}

func (cb *CircuitBreaker) Allow() error {
	cb.mu.Lock()
	defer cb.mu.Unlock()

	cb.checkAutoTransition()

	switch cb.state {
	case StateClosed:
		return nil
	case StateOpen:
		return ErrCircuitOpen
	case StateHalfOpen:
		if cb.halfOpenCalls >= cb.config.PermittedNumberOfCallsInHalfOpenState {
			return ErrCircuitHalfOpen
		}
		cb.halfOpenCalls++
		return nil
	}
	return nil
}

func (cb *CircuitBreaker) RecordSuccess() {
	cb.mu.Lock()
	defer cb.mu.Unlock()

	cb.recordResult(true)

	if cb.state == StateHalfOpen {
		cb.successes++
		if cb.successes >= cb.config.PermittedNumberOfCallsInHalfOpenState {
			cb.transitionTo(StateClosed)
		}
	} else if cb.state == StateClosed && cb.shouldTrip() {
		cb.transitionTo(StateOpen)
	}
}

func (cb *CircuitBreaker) RecordFailure() {
	cb.mu.Lock()
	defer cb.mu.Unlock()

	cb.recordResult(false)

	if cb.state == StateHalfOpen {
		cb.transitionTo(StateOpen)
		return
	}

	if cb.state == StateClosed && cb.shouldTrip() {
		cb.transitionTo(StateOpen)
	}
}

func (cb *CircuitBreaker) recordResult(success bool) {
	cb.totalCalls++

	if len(cb.callResults) >= cb.config.SlidingWindowSize {
		removed := cb.callResults[0]
		cb.callResults = cb.callResults[1:]
		if !removed {
			cb.failures--
		}
	}

	cb.callResults = append(cb.callResults, success)
	if !success {
		cb.failures++
	}
}

func (cb *CircuitBreaker) shouldTrip() bool {
	if len(cb.callResults) < cb.config.MinimumNumberOfCalls {
		return false
	}

	failureRate := float64(cb.failures) / float64(len(cb.callResults)) * 100
	return failureRate >= cb.config.FailureRateThreshold
}

func (cb *CircuitBreaker) checkAutoTransition() {
	if !cb.config.AutomaticTransitionFromOpenToHalfOpenEnabled {
		return
	}

	if cb.state == StateOpen {
		if time.Since(cb.lastStateChange) >= cb.config.WaitDurationInOpenState {
			cb.transitionTo(StateHalfOpen)
		}
	}
}

func (cb *CircuitBreaker) transitionTo(newState State) {
	if cb.state == newState {
		return
	}

	oldState := cb.state
	cb.state = newState
	cb.lastStateChange = time.Now()

	switch newState {
	case StateClosed:
		cb.failures = 0
		cb.successes = 0
		cb.totalCalls = 0
		cb.halfOpenCalls = 0
		cb.callResults = make([]bool, 0, cb.config.SlidingWindowSize)
	case StateHalfOpen:
		cb.halfOpenCalls = 0
		cb.successes = 0
	case StateOpen:
		cb.halfOpenCalls = 0
		cb.successes = 0
	}

	if cb.stateChangeNotify != nil {
		go cb.stateChangeNotify(cb.name, oldState, newState)
	}
}

func (cb *CircuitBreaker) Reset() {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	cb.transitionTo(StateClosed)
}

func (cb *CircuitBreaker) Metrics() (state State, failures, total int, failureRate float64) {
	cb.mu.RLock()
	defer cb.mu.RUnlock()

	state = cb.state
	failures = cb.failures
	total = len(cb.callResults)

	if total > 0 {
		failureRate = float64(failures) / float64(total) * 100
	}
	return
}
