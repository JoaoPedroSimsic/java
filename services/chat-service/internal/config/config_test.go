package config

import "testing"

func TestLoadFromHostPort(t *testing.T) {
	t.Setenv("CHAT_PROFILE", "dev")
	t.Setenv("DYNAMODB_HOST", "dynamodb-local")
	t.Setenv("DYNAMODB_PORT", "8000")
	t.Setenv("NATS_HOST", "nats")
	t.Setenv("NATS_PORT", "4222")
	t.Setenv("RABBITMQ_HOST", "rabbitmq")
	t.Setenv("RABBITMQ_PORT", "5672")
	t.Setenv("RABBITMQ_USERNAME", "guest")
	t.Setenv("RABBITMQ_PASSWORD", "guest")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.DynamoEndpoint != "http://dynamodb-local:8000" {
		t.Errorf("DynamoEndpoint = %q, want http://dynamodb-local:8000", cfg.DynamoEndpoint)
	}
	if cfg.NatsURL != "nats://nats:4222" {
		t.Errorf("NatsURL = %q, want nats://nats:4222", cfg.NatsURL)
	}
	if cfg.RabbitURL != "amqp://guest:guest@rabbitmq:5672/" {
		t.Errorf("RabbitURL = %q, want amqp://guest:guest@rabbitmq:5672/", cfg.RabbitURL)
	}
}

func TestLoadRejectsUnknownProfile(t *testing.T) {
	t.Setenv("CHAT_PROFILE", "invalid")
	t.Setenv("DYNAMODB_ENDPOINT", "http://localhost:8000")
	t.Setenv("NATS_URL", "nats://localhost:4222")
	t.Setenv("RABBITMQ_URL", "amqp://localhost:5672/")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for invalid profile")
	}
}
