#!/usr/bin/env bash
# Laat macOS (Safari, Chrome) de BankSim-CA vertrouwen via de login-sleutelhanger.
# Firefox gebruikt een eigen certificaatopslag; importeer daar deploy/.secrets/certs/ca.crt handmatig.
#
#   trust-ca.sh            CA toevoegen (macOS vraagt om bevestiging)
#   trust-ca.sh --remove   CA weer verwijderen
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

KEYCHAIN="$HOME/Library/Keychains/login.keychain-db"
CA_NAME="BankSim Local CA"

[[ "$(uname)" == "Darwin" ]] || fail "trust-ca.sh werkt alleen op macOS"
require security

case "${1:-}" in
  --remove)
    if security find-certificate -c "$CA_NAME" "$KEYCHAIN" >/dev/null 2>&1; then
      if [[ -f "$CERT_DIR/ca.crt" ]]; then
        security remove-trusted-cert "$CERT_DIR/ca.crt" 2>/dev/null || true
      fi
      security delete-certificate -c "$CA_NAME" "$KEYCHAIN" >/dev/null
      ok "$CA_NAME verwijderd uit de login-sleutelhanger"
    else
      ok "$CA_NAME stond niet in de login-sleutelhanger"
    fi
    ;;
  "")
    [[ -f "$CERT_DIR/ca.crt" ]] || fail "Geen CA gevonden; draai eerst install.sh"
    security add-trusted-cert -r trustRoot -p ssl -k "$KEYCHAIN" "$CERT_DIR/ca.crt"
    ok "$CA_NAME vertrouwd voor TLS (herstart de browser)"
    ;;
  -h|--help) sed -n '2,7p' "$0" ;;
  *) fail "Onbekende optie: $1" ;;
esac
