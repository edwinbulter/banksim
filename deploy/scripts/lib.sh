# Gedeelde functies voor de BankSim-scripts. Wordt gesourced, niet uitgevoerd.
# shellcheck shell=bash

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SECRETS_DIR="${BANKSIM_SECRETS_DIR:-$REPO_ROOT/deploy/.secrets}"
CERT_DIR="$SECRETS_DIR/certs"
NAMESPACE="${BANKSIM_NAMESPACE:-banksim}"
RELEASE="banksim"
KUBE_CONTEXT="${BANKSIM_CONTEXT:-kind-single-node}"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m ✓\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m !\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m ✗\033[0m %s\n' "$*" >&2; exit 1; }

require() {
  local cmd
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null || fail "Vereist commando ontbreekt: $cmd"
  done
}

kc() { kubectl --context "$KUBE_CONTEXT" "$@"; }

# Naam van het kind-cluster bij de context (kind-single-node → single-node).
kind_cluster_name() { printf '%s' "${KUBE_CONTEXT#kind-}"; }

check_context() {
  kubectl config get-contexts -o name | grep -qx "$KUBE_CONTEXT" \
    || fail "kubectl-context '$KUBE_CONTEXT' bestaat niet (kies een andere met --context)"
  kc get --raw /readyz >/dev/null 2>&1 || fail "Cluster van context '$KUBE_CONTEXT' is niet bereikbaar"
}
