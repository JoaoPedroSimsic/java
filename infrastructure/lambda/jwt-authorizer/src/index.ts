import {
  APIGatewayRequestAuthorizerEvent,
  APIGatewayAuthorizerResult,
  APIGatewayRequestAuthorizerEventV2,
  APIGatewaySimpleAuthorizerWithContextResult,
} from "aws-lambda";
import jwt, { JwtHeader, SigningKeyCallback } from "jsonwebtoken";
import jwksClient, { JwksClient, SigningKey } from "jwks-rsa";

// Environment variables
const JWKS_URL = process.env.JWKS_URL || "";
const EXPECTED_ISSUER = process.env.EXPECTED_ISSUER || "hermes-user-service";
const CACHE_TTL_MS = parseInt(process.env.CACHE_TTL_MS || "900000", 10); // 15 min default
const GATEWAY_SECRET = process.env.GATEWAY_SECRET || "";

// JWKS client with caching
let client: JwksClient | null = null;
let clientCreatedAt = 0;

interface TokenPayload {
  sub: string; // user ID from provider (Auth0/Keycloak)
  email?: string; // email claim (standard OIDC)
  iss: string;
  iat: number;
  exp: number;
}

interface AuthorizerContext {
  userEmail: string;
  userId: string;
  gatewaySecret: string;
}

function getJwksClient(): JwksClient {
  const now = Date.now();

  // Recreate client if cache expired or not initialized
  if (!client || now - clientCreatedAt > CACHE_TTL_MS) {
    console.log("Creating new JWKS client with URL:", JWKS_URL);
    client = jwksClient({
      jwksUri: JWKS_URL,
      cache: true,
      cacheMaxAge: CACHE_TTL_MS,
      rateLimit: true,
      jwksRequestsPerMinute: 10,
    });
    clientCreatedAt = now;
  }

  return client;
}

function getSigningKey(header: JwtHeader, callback: SigningKeyCallback): void {
  const jwks = getJwksClient();

  if (!header.kid) {
    callback(new Error("No key ID (kid) in token header"));
    return;
  }

  jwks.getSigningKey(header.kid, (err: Error | null, key?: SigningKey) => {
    if (err) {
      console.error("Error getting signing key:", err);
      callback(err);
      return;
    }

    if (!key) {
      callback(new Error("No signing key found"));
      return;
    }

    const signingKey = key.getPublicKey();
    callback(null, signingKey);
  });
}

function extractToken(event: APIGatewayRequestAuthorizerEvent | APIGatewayRequestAuthorizerEventV2): string | null {
  // Try cookies first (HTTP API format)
  if ("cookies" in event && event.cookies) {
    for (const cookie of event.cookies) {
      if (cookie.startsWith("jwt=")) {
        return cookie.substring(4);
      }
    }
  }

  // Try headers
  const headers = event.headers || {};

  // Check Cookie header (REST API format)
  const cookieHeader = headers.cookie || headers.Cookie;
  if (cookieHeader) {
    const cookies = cookieHeader.split(";").map((c) => c.trim());
    for (const cookie of cookies) {
      if (cookie.startsWith("jwt=")) {
        return cookie.substring(4);
      }
    }
  }

  // Check Authorization header
  const authHeader = headers.authorization || headers.Authorization;
  if (authHeader && authHeader.startsWith("Bearer ")) {
    return authHeader.substring(7);
  }

  return null;
}

function verifyToken(token: string): Promise<TokenPayload> {
  return new Promise((resolve, reject) => {
    jwt.verify(
      token,
      getSigningKey,
      {
        issuer: EXPECTED_ISSUER,
        algorithms: ["RS256"],
      },
      (err, decoded) => {
        if (err) {
          console.error("Token verification failed:", err.message);
          reject(err);
          return;
        }

        if (!decoded || typeof decoded === "string") {
          reject(new Error("Invalid token payload"));
          return;
        }

        resolve(decoded as TokenPayload);
      }
    );
  });
}

// Generate IAM policy for REST API Gateway
function generatePolicy(
  principalId: string,
  effect: "Allow" | "Deny",
  resource: string,
  context?: AuthorizerContext
): APIGatewayAuthorizerResult {
  const policy: APIGatewayAuthorizerResult = {
    principalId,
    policyDocument: {
      Version: "2012-10-17",
      Statement: [
        {
          Action: "execute-api:Invoke",
          Effect: effect,
          Resource: resource,
        },
      ],
    },
  };

  if (context) {
    policy.context = context;
  }

  return policy;
}

// Handler for REST API Gateway (v1)
export async function handler(
  event: APIGatewayRequestAuthorizerEvent
): Promise<APIGatewayAuthorizerResult> {
  console.log("Authorizer invoked for:", event.methodArn);

  const token = extractToken(event);

  if (!token) {
    console.log("No token found in request");
    return generatePolicy("anonymous", "Deny", event.methodArn);
  }

  try {
    const payload = await verifyToken(token);

    console.log("Token verified successfully for user:", payload.sub);

    return generatePolicy(payload.sub, "Allow", event.methodArn, {
      userEmail: payload.email || "",
      userId: payload.sub,
      gatewaySecret: GATEWAY_SECRET,
    });
  } catch (error) {
    console.error("Authorization failed:", error);
    return generatePolicy("anonymous", "Deny", event.methodArn);
  }
}

// Handler for HTTP API Gateway (v2) with simple response
export async function handlerV2(
  event: APIGatewayRequestAuthorizerEventV2
): Promise<APIGatewaySimpleAuthorizerWithContextResult<AuthorizerContext>> {
  console.log("Authorizer V2 invoked for:", event.routeArn);

  const token = extractToken(event);

  if (!token) {
    console.log("No token found in request");
    return {
      isAuthorized: false,
      context: {
        userEmail: "",
        userId: "",
        gatewaySecret: "",
      },
    };
  }

  try {
    const payload = await verifyToken(token);

    console.log("Token verified successfully for user:", payload.sub);

    return {
      isAuthorized: true,
      context: {
        userEmail: payload.email || "",
        userId: payload.sub,
        gatewaySecret: GATEWAY_SECRET,
      },
    };
  } catch (error) {
    console.error("Authorization failed:", error);
    return {
      isAuthorized: false,
      context: {
        userEmail: "",
        userId: "",
        gatewaySecret: "",
      },
    };
  }
}
