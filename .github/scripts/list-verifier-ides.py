#!/usr/bin/env python3
"""Emit the IDEs `verifyPlugin` should target as a compact JSON array.

`recommended()` (resolved by the printProductsReleases task) also returns the
current EAP, e.g. ["IU-2026.1.3", "IU-262.7581.18"]. We keep only released IDEs:
EAP builds relocate the @ApiStatus.Internal split-mode classes the client module
compiles against, so the plugin can't even build there. 2026.2 rejoins the matrix
automatically once it ships as IU-2026.2.x.

Feeds a GitHub Actions discover job whose output drives a one-runner-per-IDE matrix:

    - id: list
      run: echo "ides=$(python3 .github/scripts/list-verifier-ides.py)" >> "$GITHUB_OUTPUT"
"""

import json
import subprocess
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


def is_release(ide: str) -> bool:
    """A release's leading version is a calendar year (IU-2026.1.3); an EAP's is a
    build branch number (IU-262.7581.18), which is below 2000."""
    major = int(ide.split("-")[1].split(".")[0])
    return major >= 2000


def main() -> None:
    result = subprocess.run(
        ["./gradlew", "-q", "printProductsReleases"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    ides = [line for line in result.stdout.splitlines() if line]
    # printProductsReleases repeats builds across update channels; one verify run
    # per unique IDE is enough, and sorting keeps the matrix order stable.
    releases = sorted({ide for ide in ides if is_release(ide)})
    print(json.dumps(releases, separators=(",", ":")))


if __name__ == "__main__":
    main()
