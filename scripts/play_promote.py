"""Promote the newest completed release on one Play track to another, reusing the same bundle.

Usage: play_promote.py <package> <from_track> <to_track>
Reads the service account JSON from $PLAY_SERVICE_ACCOUNT_JSON.
"""
import json
import os
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"


def main(package: str, from_track: str, to_track: str) -> None:
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

    edit = call("POST", "edits")["id"]
    source = call("GET", f"edits/{edit}/tracks/{from_track}")
    completed = [r for r in source.get("releases", []) if r.get("status") == "completed"]
    if not completed:
        sys.exit(f"No completed release on {from_track} to promote")
    latest = max(completed, key=lambda r: max(int(c) for c in r["versionCodes"]))

    release = {"versionCodes": latest["versionCodes"], "status": "completed"}
    for key in ("name", "releaseNotes"):
        if key in latest:
            release[key] = latest[key]
    call("PUT", f"edits/{edit}/tracks/{to_track}", json={"track": to_track, "releases": [release]})
    call("POST", f"edits/{edit}:commit")
    print(f"Promoted {latest.get('name', '')} {latest['versionCodes']} from {from_track} to {to_track}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
