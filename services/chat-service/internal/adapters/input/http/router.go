package http

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/joaosimsic/hermes/chat-service/internal/core/ports/input"
	"go.uber.org/zap"
)

func NewRouter(chat input.ChatUseCase, logger *zap.Logger) http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.Recoverer)
	r.Use(middleware.RealIP)
	r.Use(middleware.RequestID)
	r.Use(TraceMiddleware(logger))

	r.Get("/healthz", HealthHandler)
	r.Get("/health", HealthHandler)

	r.Route("/chat", func(r chi.Router) {
		r.Use(UserIDMiddleware)
		r.Post("/conversations", notImplemented)
		r.Get("/conversations", notImplemented)
		r.Get("/conversations/{id}/messages", notImplemented)
	})

	_ = chat
	return r
}

func notImplemented(w http.ResponseWriter, r *http.Request) {
	writeError(w, r, http.StatusNotImplemented, "Not Implemented", "endpoint not implemented yet")
}
