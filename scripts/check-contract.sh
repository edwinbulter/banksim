#!/usr/bin/env bash
# Contractcheck (TO §13): faalt als backend/bank-api/src/main/resources/openapi.yaml achterwaarts incompatibel
# is veranderd ten opzichte van een eerdere versie (standaard origin/main; in CI de basis van de pull request).
#
#   scripts/check-contract.sh [git-ref]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC="backend/bank-api/src/main/resources/openapi.yaml"
basis="${1:-origin/main}"

mkdir -p "$REPO_ROOT/backend/target"
if ! git -C "$REPO_ROOT" show "$basis:$SPEC" > "$REPO_ROOT/backend/target/openapi-basis.yaml" 2>/dev/null; then
  echo "Geen $SPEC in $basis: niets om mee te vergelijken"
  exit 0
fi

echo "openapi.yaml vergelijken met $basis"
"$REPO_ROOT/backend/mvnw" -q -B -ntp -f "$REPO_ROOT/backend/pom.xml" -N -Pcontract validate
echo "Contract is achterwaarts compatibel (verschillen: backend/target/openapi-diff.md)"
