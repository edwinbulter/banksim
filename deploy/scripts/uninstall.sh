#!/usr/bin/env bash
# Verwijdert BankSim volledig uit het kind-cluster: Helm-release en namespace banksim, inclusief
# alle Secrets en de PostgreSQL-data. Raakt niets buiten de namespace.
#
#   uninstall.sh [--context <kubectl-context>] [-y] [--purge]
#     -y       niet om bevestiging vragen
#     --purge  ook de BankSim-images van de node, de lokale certificaten/CA en het CA-vertrouwen verwijderen
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

assume_yes=false
purge=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --context) KUBE_CONTEXT="$2"; shift ;;
    -y|--yes) assume_yes=true ;;
    --purge) purge=true ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) fail "Onbekende optie: $1" ;;
  esac
  shift
done

require kubectl helm
check_context

if [[ "$assume_yes" != true ]]; then
  printf 'Namespace %s (inclusief alle data) verwijderen uit %s? [j/N] ' "$NAMESPACE" "$KUBE_CONTEXT"
  read -r answer
  [[ "$answer" =~ ^[jJyY]$ ]] || { echo "Afgebroken."; exit 0; }
fi

if helm --kube-context "$KUBE_CONTEXT" -n "$NAMESPACE" status "$RELEASE" >/dev/null 2>&1; then
  info "Helm-release $RELEASE verwijderen"
  helm --kube-context "$KUBE_CONTEXT" -n "$NAMESPACE" uninstall "$RELEASE" --wait --timeout 5m >/dev/null
  ok "Release verwijderd"
fi

if kc get namespace "$NAMESPACE" >/dev/null 2>&1; then
  info "Namespace $NAMESPACE verwijderen"
  kc delete namespace "$NAMESPACE" --wait=true --timeout=5m >/dev/null
  ok "Namespace verwijderd (Secrets, PVC en data inbegrepen)"
fi

if [[ "$purge" == true ]]; then
  node="$(kind_cluster_name)-control-plane"
  if command -v docker >/dev/null && docker inspect "$node" >/dev/null 2>&1; then
    info "BankSim-images van node $node verwijderen"
    images="$(docker exec "$node" crictl images -o json \
      | sed -n 's/.*"\(docker.io\/banksim\/[^"]*\)".*/\1/p' | sort -u)"
    if [[ -n "$images" ]]; then
      # shellcheck disable=SC2086
      docker exec "$node" crictl rmi $images >/dev/null
    fi
    ok "Images verwijderd"
  fi
  "$REPO_ROOT/deploy/scripts/trust-ca.sh" --remove || warn "CA-vertrouwen niet verwijderd"
  info "Lokale certificaten verwijderen"
  rm -rf "$SECRETS_DIR"
  ok "$SECRETS_DIR verwijderd"
fi

ok "BankSim is verwijderd uit $KUBE_CONTEXT"
