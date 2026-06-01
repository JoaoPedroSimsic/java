package http

import (
	"net/http"

	"github.com/joaosimsic/hermes/chat-service/internal/pkg/trace"
	"go.uber.org/zap"
)

func TraceMiddleware(logger *zap.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			traceID := r.Header.Get(trace.IDHeader)
			if traceID == "" {
				traceID = trace.NewID()
			}

			ctx := trace.WithID(r.Context(), traceID)
			w.Header().Set(trace.IDHeader, traceID)

			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func UserIDMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		userID := r.Header.Get("X-User-Id")
		if userID == "" {
			writeError(w, r, http.StatusForbidden, "Forbidden", "missing X-User-Id")
			return
		}
		next.ServeHTTP(w, r)
	})
}
