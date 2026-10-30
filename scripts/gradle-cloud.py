#!/usr/bin/env python3
"""Run the Gradle wrapper through the inherited cloud proxy without storing credentials."""
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlsplit

root = Path(__file__).resolve().parents[1]
arguments: list[str] = []
for protocol in ("http", "https"):
    address = os.environ.get(f"{protocol.upper()}_PROXY") or os.environ.get(f"{protocol}_proxy")
    if address:
        proxy = urlsplit(address)
        if not proxy.hostname or not re.fullmatch(r"[A-Za-z0-9.-]+", proxy.hostname) or proxy.username or proxy.password:
            sys.exit("Gradle proxy requires a plain proxy host; do not persist proxy credentials")
        arguments += [f"-D{protocol}.proxyHost={proxy.hostname}", f"-D{protocol}.proxyPort={proxy.port or 80}"]
sys.exit(subprocess.call([str(root / "gradlew"), *arguments, *sys.argv[1:]], cwd=root))
