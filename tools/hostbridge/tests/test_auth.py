import tempfile
import unittest
from pathlib import Path

from tools.hostbridge.auth import extract_bearer_token, is_authorized, resolve_session_token


class AuthTest(unittest.TestCase):
    def test_extract_bearer_token_reads_authorization_header(self):
        self.assertEqual(
            "secret-token",
            extract_bearer_token({"Authorization": "Bearer secret-token"}),
        )

    def test_extract_bearer_token_rejects_non_bearer_scheme(self):
        self.assertIsNone(extract_bearer_token({"Authorization": "Basic abc"}))

    def test_is_authorized_requires_exact_match(self):
        self.assertTrue(is_authorized("secret-token", "secret-token"))
        self.assertFalse(is_authorized("secret-token", "other-token"))

    def test_resolve_session_token_prefers_environment(self):
        token = resolve_session_token(
            token_env_var="HOSTBRIDGE_TOKEN",
            env={"HOSTBRIDGE_TOKEN": "env-token"},
        )
        self.assertEqual("env-token", token)

    def test_resolve_session_token_falls_back_to_file(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            token_path = Path(tmp_dir) / "token.txt"
            token_path.write_text("file-token\n", encoding="utf-8")
            token = resolve_session_token(
                token_env_var="HOSTBRIDGE_TOKEN",
                token_file=str(token_path),
                env={},
            )
        self.assertEqual("file-token", token)


if __name__ == "__main__":
    unittest.main()
