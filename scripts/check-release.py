#!/usr/bin/env python3
"""Reject unsafe, reused, or older releases before accessing signing material."""
import os
from pathlib import Path
import subprocess

from version import release_metadata

metadata = release_metadata(os.environ["RAW_VERSION"])
if os.environ["GITHUB_EVENT_NAME"] == "workflow_dispatch" and os.environ["GITHUB_REF"] != "refs/heads/main":
    raise SystemExit("Manual releases must run from main")
head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
tag_ref = f"refs/tags/{metadata['tag']}"
tag_exists = subprocess.run(["git", "show-ref", "--verify", "--quiet", tag_ref], check=False)
if tag_exists.returncode not in (0, 1):
    raise SystemExit("Could not inspect existing release tag")
if tag_exists.returncode == 0:
    target = subprocess.check_output(["git", "rev-list", "-n", "1", str(metadata["tag"])], text=True).strip()
    if target != head:
        raise SystemExit("Existing version tag points to another commit; choose a new version")
published = subprocess.check_output([
    "gh", "api", "--paginate", f"repos/{os.environ['GITHUB_REPOSITORY']}/releases",
    "--jq", ".[] | select(.draft == false) | .tag_name",
], text=True)
for tag in published.splitlines():
    try:
        existing = release_metadata(tag)
    except ValueError:
        continue
    if existing["code"] >= metadata["code"]:
        raise SystemExit(f"Version must be newer than the published release {tag}")
with Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
    for key, value in metadata.items():
        output.write(f"{key}={value}\n")
print(f"Validated {metadata['tag']} (Android versionCode {metadata['code']})")
