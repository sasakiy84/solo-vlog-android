#!/usr/bin/env python3
"""Validate a stable APK release version before using it in Git or shell commands."""
import json
import re
import sys


def release_metadata(value: str) -> dict[str, str | int]:
    version = value.removeprefix("v")
    if not re.fullmatch(r"(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})", version):
        raise ValueError("Specify MAJOR.MINOR.PATCH without leading zeros (for example 1.2.3)")
    major, minor, patch = map(int, version.split("."))
    if major > 1999 or major + minor + patch == 0:
        raise ValueError("Version must be between 0.0.1 and 1999.999.999")
    return {"version": version, "tag": f"v{version}", "code": major * 1_000_000 + minor * 1_000 + patch}


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 scripts/version.py 1.2.3")
    try:
        print(json.dumps(release_metadata(sys.argv[1])))
    except ValueError as error:
        sys.exit(str(error))
