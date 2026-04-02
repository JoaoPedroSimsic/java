package jwt

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func TestValidator_Validate(t *testing.T) {
	privateKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("failed to generate private key: %v", err)
	}

	kid := "test-key-id"
	issuer := "test-issuer"

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		n := base64.RawURLEncoding.EncodeToString(privateKey.N.Bytes())
		e := base64.RawURLEncoding.EncodeToString([]byte{1, 0, 1})

		jwks := map[string]any{
			"keys": []map[string]any{
				{
					"kty": "RSA",
					"kid": kid,
					"use": "sig",
					"alg": "RS256",
					"n":   n,
					"e":   e,
				},
			},
		}
		_ = json.NewEncoder(w).Encode(jwks)
	}))
	defer server.Close()

	validator := NewValidator(server.URL, issuer, time.Hour)

	t.Run("Valid Token", func(t *testing.T) {
		claims := &Claims{
			RegisteredClaims: jwt.RegisteredClaims{
				Subject:   "user_123",
				Issuer:    issuer,
				ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Hour)),
			},
			Email: "test@example.com",
		}

		token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
		token.Header["kid"] = kid

		tokenString, err := token.SignedString(privateKey)
		if err != nil {
			t.Fatalf("failed to sign token: %v", err)
		}

		validatedClaims, err := validator.Validate(context.Background(), tokenString)
		if err != nil {
			t.Errorf("expected no error, got %v", err)
		}

		if validatedClaims.Subject != "user_123" {
			t.Errorf("expected subject user_123, got %s", validatedClaims.Subject)
		}
	})

	t.Run("Invalid Issuer", func(t *testing.T) {
		claims := &Claims{
			RegisteredClaims: jwt.RegisteredClaims{
				Issuer: "wrong-issuer",
			},
		}

		token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
		token.Header["kid"] = kid
		tokenString, _ := token.SignedString(privateKey)

		_, err := validator.Validate(context.Background(), tokenString)
		if err == nil {
			t.Error("expected error for invalid issuer, got nil")
		}
	})

	t.Run("Invalid Signature", func(t *testing.T) {
		wrongKey, _ := rsa.GenerateKey(rand.Reader, 2048)

		claims := &Claims{
			RegisteredClaims: jwt.RegisteredClaims{
				Issuer: issuer,
			},
		}

		token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
		token.Header["kid"] = kid
		tokenString, _ := token.SignedString(wrongKey)

		_, err := validator.Validate(context.Background(), tokenString)
		if err == nil {
			t.Error("expected error for invalid signature, got nil")
		}
	})
}
