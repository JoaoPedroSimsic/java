# Hermes K8s Quick Start Guide

## 5-Minute Setup (Development)

### 1. Prerequisites Check
```bash
# Verify tools are installed
kubectl version --client
kustomize version
docker --version

# Verify cluster is running
kubectl cluster-info
```

### 2. Create Namespace
```bash
kubectl create namespace hermes-dev
```

### 3. Setup Secrets
```bash
cd infrastructure/k8s

# Run the automated setup script
./setup-secrets.sh

# This will create all secrets.env files from templates
# Edit each one with your actual values (optional for dev, use defaults)
```

### 4. Install NGINX Ingress (if not already installed)
```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml

# Wait for ingress to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

### 5. Build and Load Images

**For Docker Desktop / Minikube:**
```bash
# From project root
eval $(minikube docker-env)  # Skip for Docker Desktop

docker build -t gateway:latest -f gateway/Dockerfile .
docker build -t auth-service:latest -f services/auth-service/Dockerfile .
docker build -t user-service:latest -f services/user-service/Dockerfile .
```

**For Kind:**
```bash
# Build images first
docker build -t gateway:latest -f gateway/Dockerfile .
docker build -t auth-service:latest -f services/auth-service/Dockerfile .
docker build -t user-service:latest -f services/user-service/Dockerfile .

# Load into kind cluster
kind load docker-image gateway:latest
kind load docker-image auth-service:latest
kind load docker-image user-service:latest
```

### 6. Deploy Everything
```bash
cd infrastructure/k8s

# Deploy all components
kubectl apply -k clusters/dev

# Or to bypass security check
kustomize build --load-restrictor LoadRestrictionsNone clusters/dev | kubectl apply -f -

# This deploys:
# - 2 PostgreSQL databases (auth-db, user-db)
# - 3 Redis instances (auth, gateway, user)
# - 1 RabbitMQ instance
# - 1 Keycloak instance
# - 3 Microservices (auth, user, gateway)
# - 1 Ingress
```

### 7. Wait for Deployment
```bash
# Watch pods come up
kubectl get pods -n hermes-dev -w

# Or check status
kubectl get all -n hermes-dev
```

All pods should be `Running` within 2-3 minutes.

### 8. Configure Local Access
```bash
# Get ingress IP
kubectl get ingress -n hermes-dev

# For Minikube
echo "$(minikube ip) hermes-dev.local" | sudo tee -a /etc/hosts

# For Docker Desktop
echo "127.0.0.1 hermes-dev.local" | sudo tee -a /etc/hosts

# For Kind (with ingress)
echo "127.0.0.1 hermes-dev.local" | sudo tee -a /etc/hosts
```

### 9. Access the Application
```bash
# Open in browser
open http://hermes-dev.local

# Or curl to test
curl http://hermes-dev.local

# Access Keycloak admin console
open http://hermes-dev.local/keycloak
# Username: admin
# Password: check shared/keycloak/overlays/dev/secrets.env
```

### 10. Verify Services
```bash
# Check all pods are running
kubectl get pods -n hermes-dev

# Check all services
kubectl get svc -n hermes-dev

# Check persistent volumes
kubectl get pvc -n hermes-dev

# View logs
kubectl logs -n hermes-dev -l app=gateway-http-gateway --tail=50
kubectl logs -n hermes-dev -l app=auth-auth-service --tail=50
kubectl logs -n hermes-dev -l app=user-user-service --tail=50
```

## Troubleshooting

### Pods in CrashLoopBackOff?
```bash
# Check logs
kubectl logs <pod-name> -n hermes-dev

# Common issues:
# 1. Database not ready - wait 30s and check again
# 2. Secrets not configured - verify secrets.env files exist
# 3. Image not found - rebuild and load images
```

### Can't Access via Ingress?
```bash
# Check ingress status
kubectl describe ingress -n hermes-dev

# Check ingress controller is running
kubectl get pods -n ingress-nginx

# Verify /etc/hosts entry
cat /etc/hosts | grep hermes-dev.local
```

### Database Connection Errors?
```bash
# Check postgres pods
kubectl get pods -n hermes-dev | grep postgres

# Check postgres logs
kubectl logs -n hermes-dev <auth-postgres-pod>

# Verify init script ran
kubectl logs -n hermes-dev <auth-postgres-pod> | grep "Database initialization"
```

### Clean Restart
```bash
# Delete everything
kubectl delete namespace hermes-dev

# Recreate
kubectl create namespace hermes-dev

# Redeploy
kubectl apply -k infrastructure/k8s/clusters/dev
```

## What Was Created?

### Namespaces
- `hermes-dev` - All application resources

### Databases
- `auth-postgres` - Auth service database
- `user-postgres` - User service database

### Caches
- `auth-redis` - Auth service cache
- `gateway-cache` - Gateway rate limiting cache
- `user-redis` - User service cache

### Services
- `gateway-http-gateway` (port 8080) - API Gateway
- `auth-auth-service` (port 8082) - Auth Service
- `user-user-service` (port 8081) - User Service
- `keycloak` (port 8180) - Identity Provider
- `rabbitmq` (ports 5672, 15672) - Message Broker

### Storage
- 5x PersistentVolumeClaims for data persistence

### Networking
- 1x Ingress for external access

## Next Steps

1. **Configure OAuth Providers** - Update GitHub OAuth credentials in Keycloak
2. **Test Authentication** - Try logging in via the frontend
3. **Check Logs** - Monitor application logs for errors
4. **Scale Services** - Try scaling replicas
5. **Deploy to Staging** - Once dev is working

## Useful Commands

```bash
# Tail logs from all services
kubectl logs -f -n hermes-dev -l component=service

# Port forward to a service (bypass ingress)
kubectl port-forward -n hermes-dev svc/gateway-http-gateway 8080:8080

# Execute into a pod
kubectl exec -it -n hermes-dev <pod-name> -- /bin/sh

# Restart a deployment
kubectl rollout restart deployment/gateway-http-gateway -n hermes-dev

# View resource usage
kubectl top pods -n hermes-dev
kubectl top nodes
```

## Documentation

- Full documentation: `infrastructure/k8s/README.md`
- Secrets reference: `infrastructure/k8s/secrets.env.example`
- Architecture diagram: Check project root README

## Need Help?

1. Check `README.md` for detailed docs
2. Review logs: `kubectl logs -n hermes-dev <pod-name>`
3. Check events: `kubectl get events -n hermes-dev`
4. Verify secrets are configured correctly

---

**Time to deploy**: ~5-10 minutes  
**Services deployed**: 8 (3 apps + 5 infrastructure)  
**Storage provisioned**: ~15-25Gi  
**Secrets to configure**: 22 files (auto-created by setup script)
