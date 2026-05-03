from __future__ import annotations

import hmac
import os
from pathlib import Path
from typing import Mapping


def extract_bearer_token(headers: Mapping[str, str]) -> str | None:
    for key, value in headers.items():
        if key.lower() != "authorization":
            continue
        scheme, _, token = value.partition(" ")
        if scheme.lower() != "bearer" or not token.strip():
            return None
        return token.strip()
    return None


def is_authorized(expected_token: str, provided_token: str | None) -> bool:
    if not expected_token or not provided_token:
        return False
    return hmac.compare_digest(expected_token, provided_token)


def resolve_session_token(
    *,
    token_env_var: str,
    token_file: str | None = None,
    env: Mapping[str, str] | None = None,
) -> str:
    source_env = env or os.environ
    token = source_env.get(token_env_var, "").strip()
    if token:
        return token
    if token_file:
        file_token = Path(token_file).read_text(encoding="utf-8").strip()
        if file_token:
            return file_token
    raise ValueError("Session token is not configured")
