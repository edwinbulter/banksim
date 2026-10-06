#!/usr/bin/env bash
# Zet de bankdata terug naar de vaste beginstand: draait bank-datagen opnieuw met modus ALTIJD (zelfde seed,
# dus exact dezelfde data) en zet de simulatiedatum terug op "vandaag", of op een vaste datum (e2e-tests).
#
#   reset-data.sh [--context <kubectl-context>] [--simulatiedatum JJJJ-MM-DD]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

simulatiedatum=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --context) KUBE_CONTEXT="$2"; shift ;;
    --simulatiedatum) simulatiedatum="$2"; shift ;;
    -h|--help) sed -n '2,5p' "$0"; exit 0 ;;
    *) fail "Onbekende optie: $1" ;;
  esac
  shift
done

require kubectl helm jq
check_context

values="$(helm --kube-context "$KUBE_CONTEXT" -n "$NAMESPACE" get values "$RELEASE" -o json 2>/dev/null)" \
  || fail "Geen installatie gevonden; draai eerst install.sh"
tag="$(jq -r '.imageTag // empty' <<<"$values")"
[[ -n "$tag" ]] || fail "Image-tag van de installatie niet gevonden"

job="bank-datagen-reset"
info "Testdata opnieuw genereren"
kc -n "$NAMESPACE" delete job "$job" --ignore-not-found --wait=true >/dev/null
helm template "$RELEASE" "$REPO_ROOT/deploy/banksim" --namespace "$NAMESPACE" \
  -s templates/bank-datagen-job.yaml \
  --set imageTag="$tag" --set datagen.mode=ALTIJD --set datagen.hook=false --set datagen.jobName="$job" \
  | kc -n "$NAMESPACE" apply -f - >/dev/null
if ! kc -n "$NAMESPACE" wait --for=condition=complete "job/$job" --timeout=10m >/dev/null; then
  kc -n "$NAMESPACE" logs "job/$job" --tail=30 >&2 || true
  fail "bank-datagen is niet gelukt"
fi
kc -n "$NAMESPACE" logs "job/$job" | grep -E "overboekingen gegenereerd|gecontroleerd" | sed 's/^.*: /   /'
kc -n "$NAMESPACE" delete job "$job" --wait=false >/dev/null

if [[ -n "$simulatiedatum" ]]; then
  [[ "$simulatiedatum" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || fail "Simulatiedatum moet JJJJ-MM-DD zijn"
  kc -n "$NAMESPACE" exec postgres-0 -- psql -U postgres -d bank -qtc \
    "UPDATE instelling SET simulatiedatum = DATE '$simulatiedatum'" >/dev/null
  ok "Simulatiedatum op $simulatiedatum gezet"
fi
ok "Testdata staat in de beginstand"
