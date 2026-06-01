package trace

import (
	"context"

	"github.com/google/uuid"
)

type contextKey string

const (
	IDKey    contextKey = "trace_id"
	IDHeader string     = "X-Trace-Id"
)

func FromContext(ctx context.Context) string {
	if id, ok := ctx.Value(IDKey).(string); ok && id != "" {
		return id
	}
	return ""
}

func WithID(ctx context.Context, traceID string) context.Context {
	return context.WithValue(ctx, IDKey, traceID)
}

func NewID() string {
	return uuid.New().String()[:8]
}

func FromHeaders(headers map[string]string) string {
	if headers == nil {
		return ""
	}
	if id := headers[IDHeader]; id != "" {
		return id
	}
	if id := headers["x-trace-id"]; id != "" {
		return id
	}
	return ""
}

func Ensure(ctx context.Context, headers map[string]string) context.Context {
	if FromContext(ctx) != "" {
		return ctx
	}
	id := FromHeaders(headers)
	if id == "" {
		id = NewID()
	}
	return WithID(ctx, id)
}
