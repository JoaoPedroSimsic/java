# API Reference

Complete reference for all HTTP and WebSocket endpoints in the Hermes platform.

## Table of Contents

- [Auth Service](#auth-service)
- [User Service](#user-service)
- [WebSocket Gateway](#websocket-gateway)
- [HTTP Gateway](#http-gateway)
- [Health & Monitoring](#health--monitoring)

---

## Auth Service

Base Path: `/auth`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/register` | Register a new user account |
| POST | `/auth/login` | Authenticate user and return tokens |
| POST | `/auth/logout` | Logout user and invalidate refresh token |
| POST | `/auth/refresh` | Refresh access token using refresh token |
| GET | `/auth/github` | Get GitHub OAuth authorization URL |
| GET | `/auth/github/callback` | Handle GitHub OAuth callback |
| GET | `/auth/me` | Get current authenticated user info |
| PATCH | `/auth/email` | Update user's email address |
| PATCH | `/auth/password` | Update user's password |

---

## User Service

Base Path: `/users`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/users` | Create a new user |
| GET | `/users` | List all users |
| GET | `/users/{id}` | Get a user by ID |
| PATCH | `/users/{id}` | Update a user's name |
| DELETE | `/users/{id}` | Delete a user |
| POST | `/users/sync` | Sync user from external source |

---

## WebSocket Gateway

Base URL: `ws://localhost:8081` (local) or `wss://ws.hermes.example.com` (production)

### Connection

| Type | Endpoint | Description |
|------|----------|-------------|
| WebSocket | `/ws` | Main WebSocket connection (requires JWT authentication) |

### Authentication

The WebSocket gateway supports three authentication methods (in priority order):

| Method | Format | Example |
|--------|--------|---------|
| Subprotocol header | `Sec-WebSocket-Protocol: bearer.<token>` | `bearer.eyJhbGci...` |
| Cookie | `access_token=<token>` | Set automatically after login |
| Query parameter | `?token=<token>` | `/ws?token=eyJhbGci...` |

### Client-to-Server Messages

Messages sent from the client to the server:

| Type | Description |
|------|-------------|
| `send_message` | Send a chat message |
| `mark_read` | Mark a message as read |
| `typing` | Send typing indicator |
| `ping` | Ping request (keep-alive) |

### Server-to-Client Messages

Messages sent from the server to the client:

| Type | Description |
|------|-------------|
| `message` | Incoming chat message |
| `pong` | Pong response to ping |
| `read_receipt` | Message read receipt |
| `presence` | User online/offline status |
| `ack` | Message acknowledgment |
| `error` | Error message |
| `rate_limit_info` | Rate limiting information |
| `system` | System messages (e.g., connection timeout) |

---

## HTTP Gateway

The HTTP Gateway (Spring Cloud Gateway) routes requests to backend services.

### Routes

| Route ID | Path Pattern | Destination |
|----------|--------------|-------------|
| auth-service | `/auth/**` | auth-service |
| user-service | `/users/**` | user-service |
| jwks | `/.well-known/jwks.json` | user-service |
| user-service-docs | `/swagger-ui/**`, `/v3/api-docs/**` | user-service |

### Fallback Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/fallback/user-service` | Fallback when user-service is unavailable |

---

## Health & Monitoring

### WebSocket Gateway

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check endpoint |
| GET | `/healthz` | Kubernetes health probe endpoint |

### Spring Actuator Endpoints

Available on auth-service, user-service, and http-gateway:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |
| `/actuator/info` | Application info |
| `/actuator/prometheus` | Prometheus metrics |
