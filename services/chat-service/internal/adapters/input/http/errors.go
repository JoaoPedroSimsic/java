package http

import (
	"encoding/json"
	"net/http"
	"time"
)

type errorResponse struct {
	Timestamp string            `json:"timestamp"`
	Status    int               `json:"status"`
	Error     string            `json:"error"`
	Message   string            `json:"message"`
	Path      string            `json:"path"`
	Details   map[string]string `json:"details"`
}

func writeError(w http.ResponseWriter, r *http.Request, status int, label, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(errorResponse{
		Timestamp: time.Now().UTC().Format("2006-01-02T15:04:05"),
		Status:    status,
		Error:     label,
		Message:   message,
		Path:      r.URL.Path,
	})
}
