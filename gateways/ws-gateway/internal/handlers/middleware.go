package handlers

import (
	"context"
	"net/http"

	"github.com/google/uuid"
)

type contextKey string

const (
	TraceIDKey    contextKey = "trace_id"
	TraceIDHeader string     = "X-Trace-Id"
)

func TraceMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		traceID := r.Header.Get(TraceIDHeader)

		if traceID == "" {
			traceID = uuid.New().String()[:8]
		}

		ctx := context.WithValue(r.Context(), TraceIDKey, traceID)

		w.Header().Set(TraceIDHeader, traceID)

		next.ServeHTTP(w, r.WithContext(ctx))
	})
}
