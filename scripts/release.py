#!/usr/bin/env python3
"""Request a versioned GitHub Actions release after committing and pushing changes."""
from pathlib import Path
import os
import subprocess
import sys

from version import release_metadata


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 scripts/release.py 1.2.3")
    try:
        metadata = release_metadata(sys.argv[1])
    except ValueError as error:
        sys.exit(str(error))
    root = Path(__file__).resolve().parents[1]
    changes = subprocess.check_output(["git", "status", "--porcelain"], cwd=root, text=True)
    if changes:
        sys.exit("Commit and push the intended changes before requesting a release")
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    remote = subprocess.check_output(["git", "ls-remote", "origin", "refs/heads/main"], cwd=root, text=True).split()
    if not remote or remote[0] != head:
        sys.exit("Local HEAD must match origin/main; push or select the intended commit first")
    github_env = dict(os.environ)
    if os.environ.get("SOLO_GITHUB_TOKEN"):
        github_env["GH_TOKEN"] = os.environ["SOLO_GITHUB_TOKEN"]
    subprocess.run([
        "gh", "workflow", "run", "release.yml", "--repo", "sasakiy84/solo-vlog-android",
        "--ref", "main", "-f", f"version={metadata['version']}",
    ], cwd=root, env=github_env, check=True)
    print(f"Requested release {metadata['tag']}. Monitor the Android release workflow until it succeeds.")


if __name__ == "__main__":
    main()
