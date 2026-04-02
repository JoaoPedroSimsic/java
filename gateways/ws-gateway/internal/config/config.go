package config

import (
	"fmt"

	"github.com/kelseyhightower/envconfig"
)

type Config struct {
	ServerPort     int    `envconfig:"WS_GATEWAY_PORT" required:"true"`
	NatsURL        string `envconfig:"NATS_URL" required:"true"`
	JwksCacheTTL   int    `envconfig:"JWKS_CACHE_TTL" required:"true"`
	LogLevel       string `envconfig:"LOG_LEVEL" required:"true"`
	ChatServiceURL string `envconfig:"CHAT_SERVICE_URL" required:"true"`
	Profile        string `envconfig:"GATEWAY_PROFILE" required:"true"`

	RateLimitAuthenticated      int `envconfig:"RATE_LIMIT_AUTHENTICATED" required:"true"`
	RateLimitAuthenticatedBurst int `envconfig:"RATE_LIMIT_AUTHENTICATED_BURST" required:"true"`

	KcJwksURL   string `envconfig:"KC_JWKS_URL"`
	KcJwtIssuer string `envconfig:"KC_JWT_ISSUER"`

	CognitoJwksURL   string `envconfig:"COGNITO_JWKS_URL"`
	CognitoJwtIssuer string `envconfig:"COGNITO_JWT_ISSUER"`

	FrontendURL        string `envconfig:"FRONTEND_URL"`
	CorsAllowedOrigins string `envconfig:"CORS_ALLOWED_ORIGINS"`

	RedisHost string `envconfig:"GATEWAY_CACHE_HOST" required:"true"`
	RedisPort int    `envconfig:"REDIS_PORT" required:"true"`

	CircuitBreakerSlidingWindowSize                     int `envconfig:"CB_SLIDING_WINDOW_SIZE" required:"true"`
	CircuitBreakerMinimumNumberOfCalls                  int `envconfig:"CB_MINIMUM_NUMBER_OF_CALLS" required:"true"`
	CircuitBreakerFailureRateThreshold                  int `envconfig:"CB_FAILURE_RATE_THRESHOLD" required:"true"`
	CircuitBreakerWaitDurationInOpenStateSeconds        int `envconfig:"CB_WAIT_DURATION_OPEN_STATE_SECONDS" required:"true"`
	CircuitBreakerPermittedNumberOfCallsInHalfOpenState int `envconfig:"CB_PERMITTED_CALLS_HALF_OPEN" required:"true"`

	TimeLimiterTimeoutSeconds    int `envconfig:"TIME_LIMITER_TIMEOUT_SECONDS" required:"true"`
	ConnectionMaxDurationMinutes int `envconfig:"CONNECTION_MAX_DURATION_MINUTES" required:"true"`
}

func Load() (*Config, error) {
	var cfg Config

	if err := envconfig.Process("", &cfg); err != nil {
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
