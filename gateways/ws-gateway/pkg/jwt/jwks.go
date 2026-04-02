package jwt

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/MicahParks/keyfunc/v3"
	"github.com/golang-jwt/jwt/v5"
)

type Claims struct {
	jwt.RegisteredClaims
	Email string `json:"email"`
	Name  string `json:"name"`
}

type Validator struct {
	jwksURL  string
	issuer   string
	cacheTTL time.Duration

	mu        sync.RWMutex
	k         keyfunc.Keyfunc
	lastFetch time.Time
}

func NewValidator(jwksURL, issuer string, cacheTTL time.Duration) *Validator {
	return &Validator{
		jwksURL:  jwksURL,
		issuer:   issuer,
		cacheTTL: cacheTTL,
	}
}

func (v *Validator) ensureKeyfunc(ctx context.Context) error {
	v.mu.RLock()
	if v.k != nil && time.Since(v.lastFetch) < v.cacheTTL {
		v.mu.RUnlock()
		return nil
	}
	v.mu.RUnlock()

	v.mu.Lock()
	defer v.mu.Unlock()

	if v.k != nil && time.Since(v.lastFetch) < v.cacheTTL {
		return nil
	}

	k, err := keyfunc.NewDefaultCtx(ctx, []string{v.jwksURL})
	if err != nil {
		return fmt.Errorf("failed to create keyfunc: %w", err)
	}

	v.k = k
	v.lastFetch = time.Now()
	return nil
}

func (v *Validator) Validate(ctx context.Context, tokenString string) (*Claims, error) {
	if err := v.ensureKeyfunc(ctx); err != nil {
		return nil, err
	}

	v.mu.RLock()
	k := v.k
	v.mu.RUnlock()

	token, err := jwt.ParseWithClaims(tokenString, &Claims{}, k.Keyfunc)
	if err != nil {
		return nil, fmt.Errorf("token validation failed: %w", err)
	}

	claims, ok := token.Claims.(*Claims)
	if !ok || !token.Valid {
		return nil, errors.New("invalid claims or token")
	}

	if claims.Issuer != v.issuer {
		return nil, fmt.Errorf("invalid issuer: expected %s, got %s", v.issuer, claims.Issuer)
	}

	return claims, nil
}
