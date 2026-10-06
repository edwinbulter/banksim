#!/usr/bin/env bash
# Scant de BankSim-images en de gebruikte Keycloak- en PostgreSQL-images met Trivy (TO §16, OWASP A03) en
# schrijft per image een CycloneDX-SBOM. Faalt bij HIGH of CRITICAL kwetsbaarheden waarvoor een fix bestaat.
#
#   scripts/scan-images.sh <image-tag>    # tag van de BankSim-images, zie "helm get values banksim -n banksim"
#
# Gebruikt een lokale trivy als die er is, anders het Trivy-image via Docker.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TRIVY_IMAGE="aquasec/trivy:0.75.0@sha256:af6acf9a6b85dfe389a1941505c0ce9efef52a4719635e1a962f022a3d855daa"
UIT="$REPO_ROOT/target/scan"
tag="${1:?Gebruik: scan-images.sh <image-tag>}"

waarde() { sed -n "s/^  $1: //p" "$REPO_ROOT/deploy/banksim/values.yaml"; }
images=(
  "banksim/bank-api:$tag" "banksim/bank-bff:$tag" "banksim/bank-migrate:$tag" "banksim/bank-datagen:$tag"
  "banksim/bank-web:$tag" "$(waarde keycloak)" "$(waarde postgres)"
)

mkdir -p "$UIT" "$REPO_ROOT/target/trivy-cache"
lokaal=false
type -P trivy >/dev/null && lokaal=true
scan() {
  if [[ "$lokaal" == true ]]; then
    trivy --cache-dir "$REPO_ROOT/target/trivy-cache" "$@"
  else
    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
      -v "$REPO_ROOT/target/trivy-cache:/cache" -v "$UIT:/uit" "$TRIVY_IMAGE" --cache-dir /cache "$@"
  fi
}
uitpad() { if [[ "$lokaal" == true ]]; then printf '%s' "$UIT/$1"; else printf '/uit/%s' "$1"; fi; }

fouten=0
for image in "${images[@]}"; do
  naam="$(basename "${image%%[:@]*}")"
  echo "==> $image"
  scan image --quiet --format cyclonedx --output "$(uitpad "$naam.cdx.json")" "$image"
  overslaan=()
  # gosu (Go) in het PostgreSQL-image stapt alleen over van root naar postgres; de StatefulSet start direct als
  # uid 70 (runAsNonRoot), dus gosu wordt nooit uitgevoerd.
  [[ "$naam" == postgres ]] && overslaan=(--skip-files usr/local/bin/gosu)
  if ! scan image --quiet --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 \
      ${overslaan[@]+"${overslaan[@]}"} "$image"; then
    fouten=$((fouten + 1))
  fi
done

echo "SBOM's per image staan in $UIT"
if (( fouten > 0 )); then
  echo "$fouten image(s) met HIGH/CRITICAL kwetsbaarheden waarvoor een fix bestaat" >&2
  exit 1
fi
echo "Geen HIGH/CRITICAL kwetsbaarheden met een beschikbare fix"
