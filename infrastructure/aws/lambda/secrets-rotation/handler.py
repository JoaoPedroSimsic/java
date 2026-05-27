"""
AWS Secrets Manager rotation Lambda (PRD Phase D).

Rotates JSON secret fields in-place using the standard four-step rotation
contract. Does not update downstream systems (Postgres, RabbitMQ, Keycloak);
pair with ESO refresh + workload restarts (see ROTATION.md / PHASE-D.md).

Secret name suffix -> JSON keys to rotate (comma-separated):
  jwt-signing-key      -> value
  rabbitmq             -> password
  keycloak-admin       -> password
  cognito              -> client_secret
"""
from __future__ import annotations

import json
import logging
import os
import secrets
import string
import time

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

SERVICE = "secretsmanager"
SECRET_KEY_MAP = {
    "jwt-signing-key": ["value"],
    "rabbitmq": ["password"],
    "keycloak-admin": ["password"],
    "cognito": ["client_secret"],
}


def _client():
    endpoint = os.environ.get("SECRETS_MANAGER_ENDPOINT")
    region = os.environ.get("AWS_REGION", "sa-east-1")
    if endpoint:
        return boto3.client(SERVICE, region_name=region, endpoint_url=endpoint)
    return boto3.client(SERVICE, region_name=region)


def _secret_suffix(secret_id: str) -> str:
    name = secret_id.split(":"secret:")[-1] if ":secret:" in secret_id else secret_id
    name = name.rstrip("/")
    return name.split("/")[-1]


def _keys_for_secret(secret_id: str) -> list[str]:
    suffix = _secret_suffix(secret_id)
    if suffix in SECRET_KEY_MAP:
        return SECRET_KEY_MAP[suffix]
    env_map = os.environ.get("ROTATION_KEY_MAP_JSON")
    if env_map:
        mapping = json.loads(env_map)
        for fragment, keys in mapping.items():
            if fragment in secret_id:
                return keys
    raise ValueError(f"No rotation key mapping for secret: {secret_id}")


def _generate_password(length: int = 32) -> str:
    alphabet = string.ascii_letters + string.digits + "!#$%&*+-=@^_"
    return "".join(secrets.choice(alphabet) for _ in range(length))


def _rotate_payload(current: dict, keys: list[str]) -> dict:
    updated = dict(current)
    for key in keys:
        updated[key] = _generate_password(48 if key == "value" else 32)
    return updated


def _get_secret_dict(client, secret_id: str, stage: str) -> dict:
    resp = client.get_secret_value(SecretId=secret_id, VersionStage=stage)
    raw = resp.get("SecretString") or "{}"
    return json.loads(raw)


def _put_secret(client, secret_id: str, token: str, payload: dict) -> None:
    client.put_secret_value(
        SecretId=secret_id,
        ClientRequestToken=token,
        SecretString=json.dumps(payload),
        VersionStages=["AWSPENDING"],
    )


def create_secret(client, secret_id: str, token: str) -> None:
    try:
        _get_secret_dict(client, secret_id, "AWSPENDING")
        logger.info("createSecret: AWSPENDING already exists for %s", secret_id)
    except client.exceptions.ResourceNotFoundException:
        current = _get_secret_dict(client, secret_id, "AWSCURRENT")
        keys = _keys_for_secret(secret_id)
        pending = _rotate_payload(current, keys)
        _put_secret(client, secret_id, token, pending)
        logger.info("createSecret: wrote AWSPENDING for %s keys=%s", secret_id, keys)


def set_secret(client, secret_id: str, token: str) -> None:
    pending = _get_secret_dict(client, secret_id, "AWSPENDING")
    _put_secret(client, secret_id, token, pending)
    logger.info("setSecret: confirmed AWSPENDING for %s", secret_id)


def test_secret(client, secret_id: str, token: str) -> None:
    pending = _get_secret_dict(client, secret_id, "AWSPENDING")
    keys = _keys_for_secret(secret_id)
    for key in keys:
        if not pending.get(key):
            raise ValueError(f"AWSPENDING missing rotated key: {key}")
    logger.info("testSecret: AWSPENDING structure valid for %s", secret_id)


def finish_secret(client, secret_id: str, token: str) -> None:
    metadata = client.describe_secret(SecretId=secret_id)
    versions = metadata.get("VersionIdsToStages", {})
    if token not in versions:
        raise ValueError(f"Version {token} not found for {secret_id}")

    if "AWSCURRENT" in versions.get(token, []):
        logger.info("finishSecret: version already AWSCURRENT")
        return

    for version_id, stages in versions.items():
        if "AWSCURRENT" in stages and version_id != token:
            client.update_secret_version_stage(
                SecretId=secret_id,
                VersionStage="AWSCURRENT",
                MoveToVersionId=token,
                RemoveFromVersionId=version_id,
            )
            logger.info("finishSecret: promoted %s to AWSCURRENT", token)
            return

    client.update_secret_version_stage(
        SecretId=secret_id,
        VersionStage="AWSCURRENT",
        MoveToVersionId=token,
    )
    logger.info("finishSecret: set AWSCURRENT to %s", token)


def lambda_handler(event, _context):
    secret_id = event["SecretId"]
    token = event["ClientRequestToken"]
    step = event["Step"]

    client = _client()

    metadata = client.describe_secret(SecretId=secret_id)
    versions = metadata.get("VersionIdsToStages", {})
    if token not in versions:
        client.put_secret_value(
            SecretId=secret_id,
            ClientRequestToken=token,
            SecretString=json.dumps(_get_secret_dict(client, secret_id, "AWSCURRENT")),
            VersionStages=["AWSPENDING"],
        )

    if "AWSCURRENT" in versions.get(token, []):
        logger.info("Version %s already AWSCURRENT; noop", token)
        return

    if "AWSPENDING" in versions.get(token, []):
        if step in ("createSecret", "setSecret"):
            logger.info("Skipping %s; AWSPENDING already set", step)
            return

    steps = {
        "createSecret": create_secret,
        "setSecret": set_secret,
        "testSecret": test_secret,
        "finishSecret": finish_secret,
    }
    steps[step](client, secret_id, token)
    time.sleep(0.1)
