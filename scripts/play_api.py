"""Minimal Google Play Developer API access for the release workflows.

Reads the service account JSON from $PLAY_SERVICE_ACCOUNT_JSON.
"""
import json
import os
import sys

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"


def client(package: str):
    """Returns call(method, path, **kw) for paths under the app; an HTTP error exits with its message."""
    # Imported here so the pure helpers in the scripts can be unit tested without these installed
    import requests
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account

    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    creds.refresh(Request())
    s = requests.Session()
    s.headers["Authorization"] = f"Bearer {creds.token}"

    def call(method: str, path: str, **kw) -> dict:
        r = s.request(method, f"{API}/{package}/{path}", **kw)
        if not r.ok:
            sys.exit(f"{method} {path} failed: {r.status_code} {r.text}")
        return r.json() if r.text else {}

    return call
