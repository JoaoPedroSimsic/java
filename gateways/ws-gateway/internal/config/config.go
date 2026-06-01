package config

import (
	"fmt"

	"github.com/caarlos0/env/v11"
)

type Config struct {
	ServerPort     int    `env:"WS_GATEWAY_PORT"`
	NatsURL        string `env:"NATS_URL"`
	JwksCacheTTL   int    `env:"JWKS_CACHE_TTL"`
	LogLevel       string `env:"LOG_LEVEL"`
	ChatServiceURL string `env:"CHAT_SERVICE_URL"`
	Profile        string `env:"GATEWAY_PROFILE"`

	RateLimitAuthenticated      int `env:"RATE_LIMIT_AUTHENTICATED"`
	RateLimitAuthenticatedBurst int `env:"RATE_LIMIT_AUTHENTICATED_BURST"`

	KcJwksURL   string `env:"KC_JWKS_URL"`
	KcJwtIssuer string `env:"KC_JWT_ISSUER"`

	CognitoJwksURL   string `env:"COGNITO_JWKS_URL"`
	CognitoJwtIssuer string `env:"COGNITO_JWT_ISSUER"`

	FrontendURL        string `env:"FRONTEND_URL"`
	CorsAllowedOrigins string `env:"CORS_ALLOWED_ORIGINS"`

	RedisHost string `env:"GATEWAY_CACHE_HOST"`
	RedisPort int    `env:"REDIS_PORT"`

	CircuitBreakerSlidingWindowSize                     int `env:"CB_SLIDING_WINDOW_SIZE"`
	CircuitBreakerMinimumNumberOfCalls                  int `env:"CB_MINIMUM_NUMBER_OF_CALLS"`
	CircuitBreakerFailureRateThreshold                  int `env:"CB_FAILURE_RATE_THRESHOLD"`
	CircuitBreakerWaitDurationInOpenStateSeconds        int `env:"CB_WAIT_DURATION_OPEN_STATE_SECONDS"`
	CircuitBreakerPermittedNumberOfCallsInHalfOpenState int `env:"CB_PERMITTED_CALLS_HALF_OPEN"`

	TimeLimiterTimeoutSeconds    int `env:"TIME_LIMITER_TIMEOUT_SECONDS"`
	ConnectionMaxDurationMinutes int `env:"CONNECTION_MAX_DURATION_MINUTES"`
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
	case "dev":
		if c.KcJwksURL == "" || c.KcJwtIssuer == "" {
			return fmt.Errorf("KC_JWKS_URL and KC_JWT_ISSUER are required for dev profile")
		}
	case "prod":
		if c.CognitoJwksURL == "" || c.CognitoJwtIssuer == "" {
			return fmt.Errorf("COGNITO_JWKS_URL and COGNITO_JWT_ISSUER are required for prod profile")
		}
		if c.CorsAllowedOrigins == "" {
			return fmt.Errorf("CORS_ALLOWED_ORIGINS is required for prod profile")
		}
	}
	return nil
}

func (c *Config) GetJwksURL() string {
	if c.Profile == "dev" {
		return c.KcJwksURL
	}
	return c.CognitoJwksURL
}

func (c *Config) GetJwtIssuer() string {
	if c.Profile == "dev" {
		return c.KcJwtIssuer
	}
	return c.CognitoJwtIssuer
}

func (c *Config) GetAllowedOrigin() string {
	if c.Profile == "dev" {
		return c.FrontendURL
	}
	return c.CorsAllowedOrigins
}

func (c *Config) GetRedisAddr() string {
	return fmt.Sprintf("%s:%d", c.RedisHost, c.RedisPort)
}
