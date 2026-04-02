package protocol

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestNewEnvelope(t *testing.T) {
	t.Run("Valid Payload", func(t *testing.T) {
		payload := SendMessagePayload{
			ConversationID: "conv_123",
			Content:        "Hello World",
			ClientMsgID:    "uuid-1",
		}

		env, err := NewEnvelope(TypeSendMessage, payload)
		if err != nil {
			t.Fatalf("Expected no error, got %v", err)
		}

		if env.Type != TypeSendMessage {
			t.Errorf("Expected type %s, got %s", TypeSendMessage, env.Type)
		}

		var decoded SendMessagePayload
		if err := json.Unmarshal(env.Payload, &decoded); err != nil {
			t.Fatalf("Failed to unmarshal payload: %v", err)
		}

		if decoded.Content != payload.Content {
			t.Errorf("Expected content %s, got %s", payload.Content, decoded.Content)
		}
	})

	t.Run("Validation Failure", func(t *testing.T) {
		payload := SendMessagePayload{}

		_, err := NewEnvelope(TypeSendMessage, payload)
		if err == nil {
			t.Fatal("Expected validation error for empty payload, got nil")
		}

		if !strings.Contains(err.Error(), "validation failed") {
			t.Errorf("Expected validation error message, got: %v", err)
		}
	})
}

func TestSendMessagePayload_Validate(t *testing.T) {
	tests := []struct {
		name    string
		payload SendMessagePayload
		wantErr bool
	}{
		{
			name: "Valid with content",
			payload: SendMessagePayload{
				ConversationID: "1",
				Content:        "hi",
			},
			wantErr: false,
		},
		{
			name: "Valid with media only",
			payload: SendMessagePayload{
				ConversationID: "1",
				MediaID:        "media_123",
			},
			wantErr: false,
		},
		{
			name: "Missing ConversationID",
			payload: SendMessagePayload{
				Content: "hi",
			},
			wantErr: true,
		},
		{
			name: "Missing both content and media",
			payload: SendMessagePayload{
				ConversationID: "1",
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.payload.Validate()
			if (err != nil) != tt.wantErr {
				t.Errorf("Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestEnvelope_Bytes(t *testing.T) {
	env := &Envelope{
		Type:    TypePing,
		Payload: json.RawMessage(`{}`),
	}

	data, err := env.Bytes()
	if err != nil {
		t.Fatalf("Bytes() failed: %v", err)
	}

	var check Envelope
	if err := json.Unmarshal(data, &check); err != nil {
		t.Fatalf("Failed to unmarshal result: %v", err)
	}

	if check.Type != TypePing {
		t.Errorf("Expected type %s, got %s", TypePing, check.Type)
	}
}
