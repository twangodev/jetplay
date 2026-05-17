#!/usr/bin/env bash
#
# Resolve the IDEs that `verifyPlugin` would target via `recommended()` and
# emit them as a compact JSON array, e.g. `["IU-2026.1.2","IU-2025.2.6.2",...]`.
#
# Intended for a GitHub Actions "discover" job that feeds a matrix:
#
#     - id: list
#       run: echo "ides=$(.github/scripts/list-verifier-ides.sh)" >> "$GITHUB_OUTPUT"
#
# Then in the downstream job:
#
#     strategy:
#       fail-fast: false
#       matrix:
#         ide: ${{ fromJSON(needs.discover.outputs.ides) }}

set -euo pipefail

cd "$(dirname "$0")/../.."

./gradlew -q printProductsReleases \
  | jq -R -s -c 'split("\n") | map(select(length > 0))'
