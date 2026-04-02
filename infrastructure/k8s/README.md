# Hermes Kubernetes Infrastructure

Professional-grade Kubernetes infrastructure for the Hermes platform with proper secrets management, environment separation, and production-ready configurations.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Directory Structure](#directory-structure)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Secrets Management](#secrets-management)
- [Deployment](#deployment)
- [Environment Configuration](#environment-configuration)
- [Troubleshooting](#troubleshooting)

## Architecture Overview

The Hermes platform consists of:

### Services
- **API Gateway** - Entry point, routing, rate limiting, JWT validation
- **Auth Service** - Authentication, OAuth2, user sessions
- **User Service** - User management, profiles, AWS S3 integration

### Shared Infrastructure
- **PostgreSQL** - Two separate instances (auth-db, user-db)
- **Redis** - Three separate instances (auth-cache, gateway-cache, user-cache)
- **RabbitMQ** - Message broker for inter-service communication
- **Keycloak** - Identity and access management
- **Ingress** - NGINX ingress controller for external access

## Directory Structure

```
infrastructure/k8s/
├── clusters/                    # Environment-specific cluster configurations
│   ├── dev/
│   │   └── kustomization.yaml  # Dev cluster composition
│   ├── staging/
│   │   └── kustomization.yaml  # Staging cluster composition
│   └── prod/
│       └── kustomization.yaml  # Production cluster composition
│
├── services/                    # Application services
│   ├── auth-service/
│   │   ├── base/               # Base Kubernetes resources
│   │   │   ├── deployment.yaml
│   │   │   ├── service.yaml
│   │   │   └── kustomization.yaml
│   │   └── overlays/           # Environment-specific configs
│   │       ├── dev/
│   │       │   ├── kustomization.yaml
│   │       │   ├── params.env          # Non-sensitive config
│   │       │   ├── secrets.env         # Secrets (gitignored)
│   │       │   └── secrets.env.example # Secret templates
│   │       ├── staging/
│   │       └── prod/
│   ├── gateway/                # Same structure
│   └── user-service/           # Same structure
│
└── shared/                      # Shared infrastructure
    ├── postgres/
    │   ├── auth-db/            # Auth service database
    │   │   ├── base/
    │   │   │   ├── deployment.yaml
    │   │   │   ├── service.yaml
    │   │   │   ├── pvc.yaml
    │   │   │   ├── configmap.yaml      # init-db.sh script
    │   │   │   └── kustomization.yaml
    │   │   └── overlays/
    │   │       ├── dev/
    │   │       ├── staging/
    │   │       └── prod/
    │   └── user-db/            # Same structure
    ├── redis/
    │   ├── auth-cache/         # Auth service cache
    │   ├── gateway-cache/      # Gateway cache
    │   └── user-cache/         # User service cache
    ├── rabbitmq/
    │   ├── base/
    │   └── overlays/
    ├── keycloak/
    │   ├── base/
    │   └── overlays/
    └── ingress/
        ├── base/
        └── overlays/
```

## Prerequisites

### Required Tools
- **kubectl** >= 1.28
- **kustomize** >= 5.0 (or use `kubectl apply -k`)
- **Docker** (for building images)
- **Kubernetes cluster** (local: minikube, kind, k3s | cloud: EKS, GKE, AKS)

### Cluster Requirements
- **NGINX Ingress Controller** installed
- **Storage provisioner** for PersistentVolumes
- **Namespaces**: `hermes-dev`, `hermes-staging`, `hermes-prod`

### Install NGINX Ingress Controller
```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml
```

### Create Namespaces
```bash
kubectl create namespace hermes-dev
kubectl create namespace hermes-staging
kubectl create namespace hermes-prod
```

## Quick Start

### 1. Clone and Navigate
```bash
cd infrastructure/k8s
```

### 2. Set Up Secrets (Development)

Copy all `secrets.env.example` files to `secrets.env` and fill in values:

```bash
# Find all secrets.env.example files
find . -name "secrets.env.example" -type f

# Copy each one to secrets.env
find . -name "secrets.env.example" -type f | while read file; do
  cp "$file" "${file%.example}"
done
```

Edit each `secrets.env` file with appropriate values. See [Secrets Management](#secrets-management) for details.

### 3. Build Docker Images

```bash
# From project root
docker build -t gateway:latest -f gateway/Dockerfile .
docker build -t auth-service:latest -f services/auth-service/Dockerfile .
docker build -t user-service:latest -f services/user-service/Dockerfile .
```

For local development (minikube/kind), load images into cluster:
```bash
# Minikube
eval $(minikube docker-env)
# Then rebuild images

# Kind
kind load docker-image gateway:latest --name your-cluster
kind load docker-image auth-service:latest --name your-cluster
kind load docker-image user-service:latest --name your-cluster
```

### 4. Deploy to Development

```bash
kubectl apply -k clusters/dev
```

### 5. Verify Deployment

```bash
kubectl get all -n hermes-dev
kubectl get pvc -n hermes-dev
kubectl get secrets -n hermes-dev
kubectl get configmaps -n hermes-dev
```

### 6. Access the Application

For local development:
```bash
# Get ingress IP
kubectl get ingress -n hermes-dev

# Add to /etc/hosts
echo "$(minikube ip) hermes-dev.local" | sudo tee -a /etc/hosts
```

Access: http://hermes-dev.local

## Secrets Management

### Overview

All secrets are managed through Kustomize's `secretGenerator` feature:
- Secrets are stored in `secrets.env` files (gitignored)
- Template files (`secrets.env.example`) are committed to git
- Secrets are automatically base64-encoded by Kubernetes
- ConfigMaps store non-sensitive configuration in `params.env`

### Master Secrets Reference

See `secrets.env.example` in the root k8s directory for a complete reference of all secrets.

### Secrets by Component

#### Postgres (auth-db & user-db)
```bash
# Location: shared/postgres/{auth-db,user-db}/overlays/{env}/secrets.env

POSTGRES_PASSWORD=secure-admin-password
FLYWAY_PASSWORD=secure-migration-password
APP_PASSWORD=secure-app-password
```

#### RabbitMQ
```bash
# Location: shared/rabbitmq/overlays/{env}/secrets.env

RABBITMQ_DEFAULT_USER=admin
RABBITMQ_DEFAULT_PASS=secure-rabbitmq-password
```

#### Keycloak
```bash
# Location: shared/keycloak/overlays/{env}/secrets.env

KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=secure-keycloak-password
GITHUB_CLIENT_ID=your-github-oauth-client-id
GITHUB_CLIENT_SECRET=your-github-oauth-client-secret
```

#### Auth Service
```bash
# Location: services/auth-service/overlays/{env}/secrets.env

SPRING_DATASOURCE_PASSWORD=secure-app-password
FLYWAY_PASSWORD=secure-migration-password
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=secure-keycloak-password
SPRING_RABBITMQ_USERNAME=admin
SPRING_RABBITMQ_PASSWORD=secure-rabbitmq-password
```

#### User Service
```bash
# Location: services/user-service/overlays/{env}/secrets.env

SPRING_DATASOURCE_PASSWORD=secure-app-password
FLYWAY_PASSWORD=secure-migration-password
SPRING_RABBITMQ_USERNAME=admin
SPRING_RABBITMQ_PASSWORD=secure-rabbitmq-password
SPRING_CLOUD_AWS_CREDENTIALS_ACCESS_KEY=your-aws-access-key
SPRING_CLOUD_AWS_CREDENTIALS_SECRET_KEY=your-aws-secret-key
```

#### API Gateway
```bash
# Location: services/gateway/overlays/{env}/secrets.env

GATEWAY_SECRET=long-secure-jwt-signing-key-min-32-chars
```

**IMPORTANT**: The `GATEWAY_SECRET` must match the `APP_JWT_SECRET` in the user-service params.env.

### Generating Strong Passwords

```bash
# Generate a 32-character password
openssl rand -base64 32

# Or use pwgen
pwgen -s 32 1

# For JWT secrets (minimum 32 characters)
openssl rand -base64 48 | tr -d "=+/" | cut -c1-32
```

### Production Secrets Best Practices

1. **Never commit secrets to version control**
2. **Use unique secrets per environment**
3. **Rotate secrets regularly** (quarterly minimum)
4. **Use a secrets management solution**:
   - HashiCorp Vault
   - AWS Secrets Manager
   - Azure Key Vault
   - Google Secret Manager
   - Kubernetes External Secrets Operator
5. **Limit access** to production secrets
6. **Audit secret access** regularly
7. **Use strong passwords** (32+ characters, mixed case, numbers, symbols)

## Deployment

### Development Environment

```bash
# Deploy everything
kubectl apply -k clusters/dev

# Or deploy individual components
kubectl apply -k shared/postgres/auth-db/overlays/dev
kubectl apply -k shared/redis/auth-redis/overlays/dev
kubectl apply -k services/auth-service/overlays/dev
```

### Staging Environment

```bash
# Ensure staging secrets are configured
# Build and tag images with staging version
docker build -t gateway:staging .
docker build -t auth-service:staging .
docker build -t user-service:staging .

# Deploy
kubectl apply -k clusters/staging
```

### Production Environment

```bash
# CRITICAL: Verify all production secrets are set
# Build and tag images with production version
docker build -t gateway:v1.0.0 .
docker build -t auth-service:v1.0.0 .
docker build -t user-service:v1.0.0 .

# Deploy with care
kubectl apply -k clusters/prod --dry-run=client
kubectl apply -k clusters/prod
```

### Update Deployments

```bash
# Update a specific service
kubectl rollout restart deployment/gateway-http-gateway -n hermes-dev

# Check rollout status
kubectl rollout status deployment/gateway-http-gateway -n hermes-dev

# Rollback if needed
kubectl rollout undo deployment/gateway-http-gateway -n hermes-dev
```

### Scaling

```bash
# Scale manually
kubectl scale deployment/auth-auth-service --replicas=3 -n hermes-prod

# Or update kustomization patches
```

## Environment Configuration

### Development
- **Namespace**: `hermes-dev`
- **Replicas**: 1 per service
- **Resources**: Minimal (128-256Mi RAM, 100-250m CPU)
- **Storage**: 1-5Gi PVCs
- **TLS**: Disabled
- **Domain**: `hermes-dev.local`

### Staging
- **Namespace**: `hermes-staging`
- **Replicas**: 2 per service (HA)
- **Resources**: Medium (256-512Mi RAM, 200-500m CPU)
- **Storage**: 5-20Gi PVCs
- **TLS**: Enabled (Let's Encrypt staging)
- **Domain**: `hermes-staging.yourdomain.com`

### Production
- **Namespace**: `hermes-prod`
- **Replicas**: 3+ per service (HA)
- **Resources**: High (512Mi-2Gi RAM, 500m-2000m CPU)
- **Storage**: 10-50Gi PVCs
- **TLS**: Enabled (Let's Encrypt production)
- **Domain**: `hermes.yourdomain.com`
- **Monitoring**: Prometheus, Grafana
- **Logging**: ELK/EFK stack

## Troubleshooting

### Check Pod Status
```bash
kubectl get pods -n hermes-dev
kubectl describe pod <pod-name> -n hermes-dev
kubectl logs <pod-name> -n hermes-dev
kubectl logs <pod-name> -n hermes-dev --previous
```

### Check Secrets
```bash
kubectl get secrets -n hermes-dev
kubectl describe secret <secret-name> -n hermes-dev

# Decode a secret
kubectl get secret <secret-name> -n hermes-dev -o jsonpath='{.data.KEY}' | base64 -d
```

### Check ConfigMaps
```bash
kubectl get configmaps -n hermes-dev
kubectl describe configmap <configmap-name> -n hermes-dev
```

### Common Issues

#### 1. ImagePullBackOff
**Cause**: Image not found or incorrect image name
**Solution**: 
- Verify image exists: `docker images | grep <image-name>`
- For local: Load into cluster (minikube/kind)
- Check imagePullPolicy in deployment

#### 2. CrashLoopBackOff
**Cause**: Application failing to start
**Solution**:
- Check logs: `kubectl logs <pod-name> -n hermes-dev`
- Verify environment variables and secrets
- Check database connectivity

#### 3. Secrets Not Found
**Cause**: secrets.env files not created
**Solution**:
```bash
# Find all missing secrets.env files
find . -name "secrets.env.example" | while read f; do
  secret_file="${f%.example}"
  if [ ! -f "$secret_file" ]; then
    echo "Missing: $secret_file"
  fi
done
```

#### 4. Database Connection Failed
**Cause**: Database not ready or wrong credentials
**Solution**:
- Check postgres pod: `kubectl get pods -n hermes-dev | grep postgres`
- Verify database initialized: `kubectl logs <postgres-pod> -n hermes-dev`
- Check password match between postgres secret and service secret

#### 5. Service Unreachable
**Cause**: Service name mismatch or networking issue
**Solution**:
- Check service exists: `kubectl get svc -n hermes-dev`
- Verify service selector matches pod labels
- Check namePrefix in kustomization (services are prefixed)

### Debug Commands

```bash
# Interactive shell in pod
kubectl exec -it <pod-name> -n hermes-dev -- /bin/sh

# Port forward for local testing
kubectl port-forward svc/gateway-http-gateway 8080:8080 -n hermes-dev

# Check DNS resolution
kubectl run -it --rm debug --image=busybox --restart=Never -n hermes-dev -- nslookup auth-postgres

# View events
kubectl get events -n hermes-dev --sort-by='.lastTimestamp'
```

## Maintenance

### Backup Databases
```bash
# Backup auth database
kubectl exec -n hermes-dev <auth-postgres-pod> -- pg_dump -U admin postgres > auth-backup.sql

# Restore
kubectl exec -i -n hermes-dev <auth-postgres-pod> -- psql -U admin postgres < auth-backup.sql
```

### Update Secrets
```bash
# Edit secrets.env file, then
kubectl delete secret <secret-name> -n hermes-dev
kubectl apply -k <path-to-overlay>

# Or use kustomize edit
kubectl rollout restart deployment/<deployment-name> -n hermes-dev
```

### Resource Cleanup
```bash
# Delete everything in namespace
kubectl delete all --all -n hermes-dev

# Delete entire namespace (careful!)
kubectl delete namespace hermes-dev
```

## Contributing

When adding new services or resources:
1. Follow the existing base/overlays structure
2. Add secrets.env.example (never secrets.env)
3. Document configuration in params.env
4. Add proper resource limits
5. Include health checks (liveness/readiness probes)
6. Update cluster kustomization files
7. Test in dev before staging/prod

## Support

For issues or questions:
- Check logs first
- Review this README
- Consult the master secrets.env.example
- Check Kubernetes events

## License

Proprietary - Hermes Platform
