import { APIGatewayProxyEventV2, APIGatewayProxyResultV2 } from 'aws-lambda';
import {
  CognitoIdentityProviderClient,
  AdminCreateUserCommand,
  AdminGetUserCommand,
  AdminSetUserPasswordCommand,
  AdminInitiateAuthCommand,
  AdminUpdateUserAttributesCommand,
} from '@aws-sdk/client-cognito-identity-provider';
import crypto from 'crypto';

const cognitoClient = new CognitoIdentityProviderClient({});

const USER_POOL_ID = process.env.USER_POOL_ID!;
const CLIENT_ID = process.env.CLIENT_ID!;
const GITHUB_CLIENT_ID = process.env.GITHUB_CLIENT_ID!;
const GITHUB_CLIENT_SECRET = process.env.GITHUB_CLIENT_SECRET!;
const CALLBACK_URL = process.env.CALLBACK_URL!;
const FRONTEND_URL = process.env.FRONTEND_URL!;

interface GitHubUser {
  id: number;
  login: string;
  email: string | null;
  name: string | null;
  avatar_url: string;
}

interface GitHubEmail {
  email: string;
  primary: boolean;
  verified: boolean;
}

interface GitHubTokenResponse {
  access_token: string;
  token_type: string;
  scope: string;
}

export const handler = async (event: APIGatewayProxyEventV2): Promise<APIGatewayProxyResultV2> => {
  const path = event.rawPath;
  const method = event.requestContext.http.method;

  try {
    if (path === '/auth/github' && method === 'GET') {
      return handleInitiate(event);
    } else if (path === '/auth/github/callback' && method === 'GET') {
      return await handleCallback(event);
    } else {
      return {
        statusCode: 404,
        headers: corsHeaders(),
        body: JSON.stringify({ error: 'Not found' }),
      };
    }
  } catch (error) {
    console.error('Error:', error);
    return {
      statusCode: 500,
      headers: corsHeaders(),
      body: JSON.stringify({ error: 'Internal server error' }),
    };
  }
};

function handleInitiate(): APIGatewayProxyResultV2 {
  const state = crypto.randomBytes(16).toString('hex');
  
  const params = new URLSearchParams({
    client_id: GITHUB_CLIENT_ID,
    redirect_uri: CALLBACK_URL,
    scope: 'user:email read:user',
    state: state,
  });

  const githubAuthUrl = `https://github.com/login/oauth/authorize?${params.toString()}`;

  return {
    statusCode: 302,
    headers: {
      ...corsHeaders(),
      Location: githubAuthUrl,
      'Set-Cookie': `oauth_state=${state}; HttpOnly; Secure; SameSite=Lax; Max-Age=600`,
    },
    body: '',
  };
}

async function handleCallback(event: APIGatewayProxyEventV2): Promise<APIGatewayProxyResultV2> {
  const code = event.queryStringParameters?.code;
  const error = event.queryStringParameters?.error;

  if (error) {
    return redirectToFrontend({ error: 'GitHub authentication denied' });
  }

  if (!code) {
    return redirectToFrontend({ error: 'Missing authorization code' });
  }

  const tokenResponse = await fetch('https://github.com/login/oauth/access_token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      client_id: GITHUB_CLIENT_ID,
      client_secret: GITHUB_CLIENT_SECRET,
      code: code,
    }),
  });

  const tokenData = (await tokenResponse.json()) as GitHubTokenResponse;

  if (!tokenData.access_token) {
    return redirectToFrontend({ error: 'Failed to get GitHub access token' });
  }

  const userResponse = await fetch('https://api.github.com/user', {
    headers: {
      Authorization: `Bearer ${tokenData.access_token}`,
      Accept: 'application/json',
    },
  });

  const githubUser = (await userResponse.json()) as GitHubUser;

  let email = githubUser.email;
  if (!email) {
    const emailsResponse = await fetch('https://api.github.com/user/emails', {
      headers: {
        Authorization: `Bearer ${tokenData.access_token}`,
        Accept: 'application/json',
      },
    });

    const emails = (await emailsResponse.json()) as GitHubEmail[];
    const primaryEmail = emails.find((e) => e.primary && e.verified);
    email = primaryEmail?.email || emails[0]?.email;
  }

  if (!email) {
    return redirectToFrontend({ error: 'Could not retrieve email from GitHub' });
  }

  const cognitoTokens = await createOrUpdateCognitoUser({
    email,
    githubId: githubUser.id.toString(),
    githubLogin: githubUser.login,
    name: githubUser.name || githubUser.login,
  });

  return redirectToFrontend({
    access_token: cognitoTokens.accessToken,
    id_token: cognitoTokens.idToken,
    refresh_token: cognitoTokens.refreshToken,
  });
}

async function createOrUpdateCognitoUser(params: {
  email: string;
  githubId: string;
  githubLogin: string;
  name: string;
}): Promise<{ accessToken: string; idToken: string; refreshToken: string }> {
  const { email, githubId, githubLogin, name } = params;
  const username = `github_${githubId}`;
  const tempPassword = crypto.randomBytes(32).toString('base64') + '!Aa1';

  try {
    await cognitoClient.send(
      new AdminGetUserCommand({
        UserPoolId: USER_POOL_ID,
        Username: username,
      })
    );

    await cognitoClient.send(
      new AdminUpdateUserAttributesCommand({
        UserPoolId: USER_POOL_ID,
        Username: username,
        UserAttributes: [
          { Name: 'email', Value: email },
          { Name: 'email_verified', Value: 'true' },
          { Name: 'name', Value: name },
          { Name: 'preferred_username', Value: githubLogin },
          { Name: 'custom:github_id', Value: githubId },
        ],
      })
    );
  } catch (error) {
    if ((error as any).name === 'UserNotFoundException') {
      await cognitoClient.send(
        new AdminCreateUserCommand({
          UserPoolId: USER_POOL_ID,
          Username: username,
          UserAttributes: [
            { Name: 'email', Value: email },
            { Name: 'email_verified', Value: 'true' },
            { Name: 'name', Value: name },
            { Name: 'preferred_username', Value: githubLogin },
            { Name: 'custom:github_id', Value: githubId },
          ],
          MessageAction: 'SUPPRESS',
        })
      );

      await cognitoClient.send(
        new AdminSetUserPasswordCommand({
          UserPoolId: USER_POOL_ID,
          Username: username,
          Password: tempPassword,
          Permanent: true,
        })
      );
    } else {
      throw error;
    }
  }

  const authResponse = await cognitoClient.send(
    new AdminInitiateAuthCommand({
      UserPoolId: USER_POOL_ID,
      ClientId: CLIENT_ID,
      AuthFlow: 'ADMIN_USER_PASSWORD_AUTH',
      AuthParameters: {
        USERNAME: username,
        PASSWORD: tempPassword,
      },
    })
  );

  await cognitoClient.send(
    new AdminSetUserPasswordCommand({
      UserPoolId: USER_POOL_ID,
      Username: username,
      Password: tempPassword,
      Permanent: true,
    })
  );

  const finalAuthResponse = await cognitoClient.send(
    new AdminInitiateAuthCommand({
      UserPoolId: USER_POOL_ID,
      ClientId: CLIENT_ID,
      AuthFlow: 'ADMIN_USER_PASSWORD_AUTH',
      AuthParameters: {
        USERNAME: username,
        PASSWORD: tempPassword,
      },
    })
  );

  return {
    accessToken: finalAuthResponse.AuthenticationResult?.AccessToken || '',
    idToken: finalAuthResponse.AuthenticationResult?.IdToken || '',
    refreshToken: finalAuthResponse.AuthenticationResult?.RefreshToken || '',
  };
}

function redirectToFrontend(params: Record<string, string | undefined>): APIGatewayProxyResultV2 {
  const filteredParams: Record<string, string> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) {
      filteredParams[key] = value;
    }
  }
  
  const queryString = new URLSearchParams(filteredParams).toString();
  const redirectUrl = `${FRONTEND_URL}/auth/callback?${queryString}`;

  return {
    statusCode: 302,
    headers: {
      ...corsHeaders(),
      Location: redirectUrl,
    },
    body: '',
  };
}

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type,Authorization',
    'Access-Control-Allow-Methods': 'GET,OPTIONS',
  };
}
