"""Promote the release built from one source tree from a Play track to another, reusing its bundle.

Usage: play_promote.py <package> <from_track> <to_track> <tree>
<tree> is `git rev-parse HEAD^{tree}` of the commit being promoted. release-internal.yml names each release
"<versionCode> (tree <first 12 hex digits>)", so what gets promoted is exactly what that commit contains,
not just the newest upload (dev may have moved on since).
"""
import sys

from play_api import client


def pick_release(releases: list[dict], tree: str) -> dict | None:
    """The newest completed release built from [tree], or None if there isn't one."""
    tag = f"tree {tree[:12]}"
    built = [r for r in releases if r.get("status") == "completed" and tag in r.get("name", "")]
    return max(built, key=lambda r: max(int(c) for c in r["versionCodes"]), default=None)


def main(package: str, from_track: str, to_track: str, tree: str) -> None:
    call = client(package)
    edit = call("POST", "edits")["id"]
    source = call("GET", f"edits/{edit}/tracks/{from_track}")
    release = pick_release(source.get("releases", []), tree)
    if release is None:
        found = [r.get("name", "") for r in source.get("releases", [])]
        sys.exit(
            f"No completed {from_track} release was built from this source (tree {tree[:12]}); {from_track} has {found}. "
            f"Wait for (or re-run) the {from_track} upload of this source, then re-run this workflow."
        )

    promoted = {"versionCodes": release["versionCodes"], "status": "completed"}
    for key in ("name", "releaseNotes"):
        if key in release:
            promoted[key] = release[key]
    call("PUT", f"edits/{edit}/tracks/{to_track}", json={"track": to_track, "releases": [promoted]})
    call("POST", f"edits/{edit}:commit")
    print(f"Promoted {release.get('name', '')} {release['versionCodes']} from {from_track} to {to_track}")


if __name__ == "__main__":
    main(*sys.argv[1:5])
