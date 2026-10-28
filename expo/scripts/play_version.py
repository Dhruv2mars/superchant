"""Print NN, the build number in versionCode YYDDDNN, for the next Play upload on day YYDDD.

Usage: play_version.py <package> <YYDDD>
NN is one more than the highest versionCode Play already has for that day (0 if none), so uploads only
ever go up and no code is reused, however close together they are or how often a run is retried.
"""
import sys

from play_api import client


def next_build_number(day: int, used: list[int]) -> int:
    base = day * 100
    highest = max(used, default=0)
    number = max(highest + 1 - base, 0)
    if number > 99:
        raise ValueError(f"No build number left for day {day}: Play already has versionCode {highest}")
    return number


def used_version_codes(call) -> list[int]:
    """Every versionCode Play knows: uploaded bundles and APKs, and the releases on each track."""
    edit = call("POST", "edits")["id"]
    try:
        codes = [int(b["versionCode"]) for b in call("GET", f"edits/{edit}/bundles").get("bundles", [])]
        codes += [int(a["versionCode"]) for a in call("GET", f"edits/{edit}/apks").get("apks", [])]
        for track in call("GET", f"edits/{edit}/tracks").get("tracks", []):
            for release in track.get("releases", []):
                codes += [int(c) for c in release.get("versionCodes", [])]
    finally:
        call("DELETE", f"edits/{edit}")
    return codes


if __name__ == "__main__":
    package, day = sys.argv[1], int(sys.argv[2])
    try:
        print(next_build_number(day, used_version_codes(client(package))))
    except ValueError as e:
        sys.exit(str(e))
