# AWS Cognito Setup for Hermes

This Terraform configuration sets up Amazon Cognito as the production Identity Provider (IdP) for Hermes.

## Prerequisites

1. AWS CLI configured with appropriate credentials
2. Terraform >= 1.0 installed
3. A GitHub OAuth App for social login

## GitHub OAuth App Setup

Before deploying, create a GitHub OAuth App:

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click "New OAuth App"
3. Fill in the details:
   - **Application name**: `Hermes Production`
   - **Homepage URL**: `https://hermes-app.com`
   - **Authorization callback URL**: `https://hermes-auth-prod.auth.us-east-1.amazoncognito.com/oauth2/idpresponse`
     (Replace `hermes-auth-prod` with your `cognito_domain_prefix-environment` and `us-east-1` with your region)
4. Save the Client ID and Client Secret

## Deployment

### 1. Configure Variables

```bash
cd infrastructure/terraform/cognito
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your values:
- Update `github_client_id` and `github_client_secret`
- Adjust `callback_urls` and `logout_urls` for your frontend
- Customize `cognito_domain_prefix` (must be globally unique)

### 2. Initialize Terraform

```bash
terraform init
```

### 3. Review the Plan

```bash
terraform plan
```

### 4. Apply Configuration

```bash
terraform apply
```

### 5. Get Output Values

After successful deployment:

```bash
terraform output
```

This will display:
- `user_pool_id` - Cognito User Pool ID
- `client_id` - App Client ID for frontend
- `jwks_url` - JWKS URL for gateway JWT validation
- `issuer` - JWT issuer for token validation
- `cognito_domain` - OAuth2 domain
- `github_login_url` - Direct URL for GitHub login

## Gateway Configuration

Update your production environment variables with the Terraform outputs:

```bash
export COGNITO_JWKS_URL=$(terraform output -raw jwks_url)
export COGNITO_ISSUER=$(terraform output -raw issuer)
```

Or add to your `.env` file:
```
COGNITO_JWKS_URL=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXX/.well-known/jwks.json
COGNITO_ISSUER=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXX
```

## Frontend Integration

### OAuth2 Endpoints

| Endpoint | URL |
|----------|-----|
| Authorize | `https://{domain}.auth.{region}.amazoncognito.com/oauth2/authorize` |
| Token | `https://{domain}.auth.{region}.amazoncognito.com/oauth2/token` |
| UserInfo | `https://{domain}.auth.{region}.amazoncognito.com/oauth2/userInfo` |
| Logout | `https://{domain}.auth.{region}.amazoncognito.com/logout` |

### GitHub Social Login Flow

1. Redirect user to:
   ```
   https://{domain}.auth.{region}.amazoncognito.com/oauth2/authorize
     ?identity_provider=GitHub
     &response_type=code
     &client_id={client_id}
     &redirect_uri={callback_url}
     &scope=email+openid+profile
   ```

2. After GitHub authentication, user is redirected to your callback URL with an authorization code

3. Exchange the code for tokens:
   ```bash
   POST https://{domain}.auth.{region}.amazoncognito.com/oauth2/token
   Content-Type: application/x-www-form-urlencoded

   grant_type=authorization_code
   &client_id={client_id}
   &code={authorization_code}
   &redirect_uri={callback_url}
   &code_verifier={pkce_code_verifier}
   ```

### Recommended Libraries

- **React/Vue/Angular**: `@aws-amplify/auth` or `amazon-cognito-identity-js`
- **Plain JavaScript**: `amazon-cognito-identity-js`

## JWT Token Claims

Cognito tokens include these claims (compatible with the gateway):

| Claim | Description |
|-------|-------------|
| `sub` | User ID (UUID) - used as `X-User-Id` header |
| `email` | User email - used as `X-User-Email` header |
| `email_verified` | Email verification status |
| `cognito:username` | Username (email in our case) |
| `name` | User's display name |

## Security Notes

- The app client is configured as a **public client** (no client secret) with PKCE
- Access tokens expire in 5 minutes (matching Keycloak dev config)
- Refresh tokens are valid for 30 days
- MFA is set to OPTIONAL by default (recommended to enable in production)

## Cleanup

To destroy all resources:

```bash
terraform destroy
```

**Warning**: In production, `deletion_protection` is enabled. You'll need to:
1. Set `deletion_protection = "INACTIVE"` in `main.tf`
2. Run `terraform apply`
3. Then run `terraform destroy`
