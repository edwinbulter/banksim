#!/usr/bin/env bash
# Controleert of de gegenereerde Angular-client (frontend/src/app/api) bij openapi.yaml past (TO §13).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
npm --prefix "$REPO_ROOT/frontend" run generate:api >/dev/null
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain -- frontend/src/app/api)" ]]; then
  git -C "$REPO_ROOT" status --short -- frontend/src/app/api
  echo "De gegenereerde API-client is verouderd: draai 'npm --prefix frontend run generate:api' en commit het resultaat" >&2
  exit 1
fi
echo "Gegenereerde API-client is actueel"
