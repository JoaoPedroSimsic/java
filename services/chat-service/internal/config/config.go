package config

import (
	"fmt"
	"net/url"

	"github.com/caarlos0/env/v11"
)

type Config struct {
	Profile    string `env:"CHAT_PROFILE,required"`
	ServerPort int    `env:"CHAT_SERVICE_PORT" envDefault:"8085"`
	LogLevel   string `env:"LOG_LEVEL" envDefault:"info"`

	DynamoEndpoint string `env:"DYNAMODB_ENDPOINT"`
	DynamoHost     string `env:"DYNAMODB_HOST"`
	DynamoPort     int    `env:"DYNAMODB_PORT" envDefault:"8000"`
	DynamoTable    string `env:"DYNAMODB_TABLE" envDefault:"hermes-chat"`

	NatsURL  string `env:"NATS_URL"`
	NatsHost string `env:"NATS_HOST"`
	NatsPort int    `env:"NATS_PORT" envDefault:"4222"`

	RabbitURL      string `env:"RABBITMQ_URL"`
	RabbitHost     string `env:"RABBITMQ_HOST"`
	RabbitPort     int    `env:"RABBITMQ_PORT" envDefault:"5672"`
	RabbitUsername string `env:"RABBITMQ_USERNAME"`
	RabbitPassword string `env:"RABBITMQ_PASSWORD"`
}

func Load() (*Config, error) {
	var cfg Config
	if err := env.Parse(&cfg); err != nil {
		return nil, err
	}
	if err := cfg.validate(); err != nil {
		return nil, err
	}
	return &cfg, nil
}

func (c *Config) validate() error {
	switch c.Profile {
	case "dev", "staging", "prod":
	default:
		return fmt.Errorf("unsupported CHAT_PROFILE: %q", c.Profile)
	}

	var err error
	if c.DynamoEndpoint, err = resolveHTTPEndpoint(c.DynamoEndpoint, c.DynamoHost, c.DynamoPort, "http"); err != nil {
		return fmt.Errorf("dynamodb endpoint: %w", err)
	}
	if c.NatsURL, err = resolveNATSEndpoint(c.NatsURL, c.NatsHost, c.NatsPort); err != nil {
		return fmt.Errorf("nats url: %w", err)
	}
	if c.RabbitURL, err = resolveRabbitURL(c); err != nil {
		return fmt.Errorf("rabbitmq url: %w", err)
	}
	return nil
}

func resolveHTTPEndpoint(explicit, host string, port int, scheme string) (string, error) {
	if explicit != "" {
		return explicit, nil
	}
	if host == "" {
		return "", fmt.Errorf("DYNAMODB_ENDPOINT or DYNAMODB_HOST is required")
	}
	return fmt.Sprintf("%s://%s:%d", scheme, host, port), nil
}

func resolveNATSEndpoint(explicit, host string, port int) (string, error) {
	if explicit != "" {
		return explicit, nil
	}
	if host == "" {
		return "", fmt.Errorf("NATS_URL or NATS_HOST is required")
	}
	return fmt.Sprintf("nats://%s:%d", host, port), nil
}

func resolveRabbitURL(c *Config) (string, error) {
	if c.RabbitURL != "" {
		return c.RabbitURL, nil
	}
	if c.RabbitHost == "" {
		return "", fmt.Errorf("RABBITMQ_URL or RABBITMQ_HOST is required")
	}
	u := &url.URL{
		Scheme: "amqp",
		Host:   fmt.Sprintf("%s:%d", c.RabbitHost, c.RabbitPort),
		Path:   "/",
	}
	if c.RabbitUsername != "" {
		if c.RabbitPassword != "" {
			u.User = url.UserPassword(c.RabbitUsername, c.RabbitPassword)
		} else {
			u.User = url.User(c.RabbitUsername)
		}
	}
	return u.String(), nil
}
