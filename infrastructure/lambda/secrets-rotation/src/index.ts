import {
  DescribeSecretCommand,
  GetSecretValueCommand,
  PutSecretValueCommand,
  ResourceNotFoundException,
  SecretsManagerClient,
  UpdateSecretVersionStageCommand,
} from "@aws-sdk/client-secrets-manager";
import type { Handler } from "aws-lambda";
import crypto from "crypto";

const SECRET_KEY_MAP: Record<string, string[]> = {
  "jwt-signing-key": ["value"],
  rabbitmq: ["password"],
  "keycloak-admin": ["password"],
  cognito: ["client_secret"],
};

const PASSWORD_ALPHABET =
  "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&*+-=@^_";

interface RotationEvent {
  SecretId: string;
  ClientRequestToken: string;
  Step: "createSecret" | "setSecret" | "testSecret" | "finishSecret";
}

function secretsManagerClient(): SecretsManagerClient {
  const region = process.env.AWS_REGION ?? "sa-east-1";
  const endpoint = process.env.SECRETS_MANAGER_ENDPOINT;
  return new SecretsManagerClient({
    region,
    ...(endpoint ? { endpoint } : {}),
  });
}

function secretSuffix(secretId: string): string {
  let name = secretId.includes(":secret:")
    ? secretId.split(":secret:").pop()!
    : secretId;
  name = name.replace(/\/$/, "");
  return name.split("/").pop()!;
}

function keysForSecret(secretId: string): string[] {
  const suffix = secretSuffix(secretId);
  if (SECRET_KEY_MAP[suffix]) {
    return SECRET_KEY_MAP[suffix];
  }
  const envMap = process.env.ROTATION_KEY_MAP_JSON;
  if (envMap) {
    const mapping = JSON.parse(envMap) as Record<string, string[]>;
    for (const [fragment, keys] of Object.entries(mapping)) {
      if (secretId.includes(fragment)) {
        return keys;
      }
    }
  }
  throw new Error(`No rotation key mapping for secret: ${secretId}`);
}

function generatePassword(length: number): string {
  const bytes = crypto.randomBytes(length);
  let result = "";
  for (let i = 0; i < length; i++) {
    result += PASSWORD_ALPHABET[bytes[i]! % PASSWORD_ALPHABET.length];
  }
  return result;
}

function rotatePayload(current: Record<string, unknown>, keys: string[]): Record<string, unknown> {
  const updated = { ...current };
  for (const key of keys) {
    updated[key] = generatePassword(key === "value" ? 48 : 32);
  }
  return updated;
}

async function getSecretDict(
  client: SecretsManagerClient,
  secretId: string,
  stage: string,
): Promise<Record<string, unknown>> {
  const resp = await client.send(
    new GetSecretValueCommand({ SecretId: secretId, VersionStage: stage }),
  );
  return JSON.parse(resp.SecretString ?? "{}") as Record<string, unknown>;
}

async function putSecret(
  client: SecretsManagerClient,
  secretId: string,
  token: string,
  payload: Record<string, unknown>,
): Promise<void> {
  await client.send(
    new PutSecretValueCommand({
      SecretId: secretId,
      ClientRequestToken: token,
      SecretString: JSON.stringify(payload),
      VersionStages: ["AWSPENDING"],
    }),
  );
}

async function createSecret(
  client: SecretsManagerClient,
  secretId: string,
  token: string,
): Promise<void> {
  try {
    await getSecretDict(client, secretId, "AWSPENDING");
    console.log(`createSecret: AWSPENDING already exists for ${secretId}`);
  } catch (err) {
    if (!(err instanceof ResourceNotFoundException)) {
      throw err;
    }
    const current = await getSecretDict(client, secretId, "AWSCURRENT");
    const keys = keysForSecret(secretId);
    const pending = rotatePayload(current, keys);
    await putSecret(client, secretId, token, pending);
    console.log(`createSecret: wrote AWSPENDING for ${secretId} keys=${keys.join(",")}`);
  }
}

async function setSecret(
  client: SecretsManagerClient,
  secretId: string,
  token: string,
): Promise<void> {
  const pending = await getSecretDict(client, secretId, "AWSPENDING");
  await putSecret(client, secretId, token, pending);
  console.log(`setSecret: confirmed AWSPENDING for ${secretId}`);
}

async function testSecret(
  client: SecretsManagerClient,
  secretId: string,
  _token: string,
): Promise<void> {
  const pending = await getSecretDict(client, secretId, "AWSPENDING");
  const keys = keysForSecret(secretId);
  for (const key of keys) {
    if (!pending[key]) {
      throw new Error(`AWSPENDING missing rotated key: ${key}`);
    }
  }
  console.log(`testSecret: AWSPENDING structure valid for ${secretId}`);
}

async function finishSecret(
  client: SecretsManagerClient,
  secretId: string,
  token: string,
): Promise<void> {
  const metadata = await client.send(new DescribeSecretCommand({ SecretId: secretId }));
  const versions = metadata.VersionIdsToStages ?? {};

  if (!(token in versions)) {
    throw new Error(`Version ${token} not found for ${secretId}`);
  }

  if (versions[token]?.includes("AWSCURRENT")) {
    console.log("finishSecret: version already AWSCURRENT");
    return;
  }

  for (const [versionId, stages] of Object.entries(versions)) {
    if (stages?.includes("AWSCURRENT") && versionId !== token) {
      await client.send(
        new UpdateSecretVersionStageCommand({
          SecretId: secretId,
          VersionStage: "AWSCURRENT",
          MoveToVersionId: token,
          RemoveFromVersionId: versionId,
        }),
      );
      console.log(`finishSecret: promoted ${token} to AWSCURRENT`);
      return;
    }
  }

  await client.send(
    new UpdateSecretVersionStageCommand({
      SecretId: secretId,
      VersionStage: "AWSCURRENT",
      MoveToVersionId: token,
    }),
  );
  console.log(`finishSecret: set AWSCURRENT to ${token}`);
}

export const handler: Handler<RotationEvent> = async (event) => {
  const { SecretId: secretId, ClientRequestToken: token, Step: step } = event;
  const client = secretsManagerClient();

  const metadata = await client.send(new DescribeSecretCommand({ SecretId: secretId }));
  const versions = metadata.VersionIdsToStages ?? {};

  if (!(token in versions)) {
    const current = await getSecretDict(client, secretId, "AWSCURRENT");
    await client.send(
      new PutSecretValueCommand({
        SecretId: secretId,
        ClientRequestToken: token,
        SecretString: JSON.stringify(current),
        VersionStages: ["AWSPENDING"],
      }),
    );
  }

  if (versions[token]?.includes("AWSCURRENT")) {
    console.log(`Version ${token} already AWSCURRENT; noop`);
    return;
  }

  if (versions[token]?.includes("AWSPENDING")) {
    if (step === "createSecret" || step === "setSecret") {
      console.log(`Skipping ${step}; AWSPENDING already set`);
      return;
    }
  }

  const steps: Record<RotationEvent["Step"], () => Promise<void>> = {
    createSecret: () => createSecret(client, secretId, token),
    setSecret: () => setSecret(client, secretId, token),
    testSecret: () => testSecret(client, secretId, token),
    finishSecret: () => finishSecret(client, secretId, token),
  };

  await steps[step]();
  await new Promise((resolve) => setTimeout(resolve, 100));
};
