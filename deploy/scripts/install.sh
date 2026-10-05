#!/usr/bin/env bash
# Installeert BankSim in namespace banksim van het kind-cluster single-node (TO §15.2).
# Idempotent: opnieuw draaien werkt de installatie bij.
#
#   install.sh [--context <kubectl-context>] [--skip-build] [--rotate-certs] [--skip-smoke-tests]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

skip_build=false
rotate_certs=false
smoke_tests=true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --context) KUBE_CONTEXT="$2"; shift ;;
    --skip-build) skip_build=true ;;
    --rotate-certs) rotate_certs=true ;;
    --skip-smoke-tests) smoke_tests=false ;;
    -h|--help) sed -n '2,6p' "$0"; exit 0 ;;
    *) fail "Onbekende optie: $1" ;;
  esac
  shift
done

COMPONENTS=(bank-web bank-bff bank-api bank-migrate bank-datagen keycloak postgres)

check_prerequisites() {
  info "Vereisten controleren"
  require kubectl helm docker kind openssl keytool curl git jq
  check_context
  kc -n ingress-nginx get deployment ingress-nginx-controller >/dev/null 2>&1 \
    || fail "ingress-nginx ontbreekt in het cluster (namespace ingress-nginx)"
  ok "Context $KUBE_CONTEXT, ingress-nginx aanwezig"
}

image_tag() {
  local tag
  tag="$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
  if [[ -n "$(git -C "$REPO_ROOT" status --porcelain -- backend frontend)" ]]; then
    tag="$tag-dirty-$(date +%s)"
  fi
  printf '%s' "$tag"
}

build_images() {
  local tag="$1" arch platform
  arch="$(docker info --format '{{.Architecture}}')"
  case "$arch" in
    aarch64|arm64) platform="linux/arm64" ;;
    *) platform="linux/amd64" ;;
  esac
  info "Images bouwen ($tag, $platform)"
  "$REPO_ROOT/backend/mvnw" -q -B -ntp -f "$REPO_ROOT/backend/pom.xml" \
    -pl bank-api,bank-bff,bank-migrate,bank-datagen -am package jib:dockerBuild \
    -DskipTests -Dimage.tag="$tag" -Djib.from.platforms="$platform"
  docker build -q --platform "$platform" -t "banksim/bank-web:$tag" "$REPO_ROOT/frontend" >/dev/null
  ok "Images gebouwd"

  info "Images laden in kind-cluster $(kind_cluster_name)"
  kind load docker-image --name "$(kind_cluster_name)" \
    "banksim/bank-api:$tag" "banksim/bank-bff:$tag" "banksim/bank-migrate:$tag" "banksim/bank-datagen:$tag" \
    "banksim/bank-web:$tag" >/dev/null
  ok "Images geladen"
}

create_namespace() {
  info "Namespace $NAMESPACE"
  kc apply -f - >/dev/null <<YAML
apiVersion: v1
kind: Namespace
metadata:
  name: $NAMESPACE
  labels:
    app.kubernetes.io/part-of: banksim
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/enforce-version: latest
YAML
  ok "Namespace met Pod Security Standard 'restricted'"
}

apply_secret() {
  kc -n "$NAMESPACE" create secret "$@" --dry-run=client -o yaml | kc apply -f - >/dev/null
}

create_tls_secrets() {
  info "Certificaten"
  if [[ "$rotate_certs" == true ]]; then
    "$REPO_ROOT/deploy/scripts/certs.sh" --rotate
  else
    "$REPO_ROOT/deploy/scripts/certs.sh"
  fi
  local comp dir
  for comp in "${COMPONENTS[@]}"; do
    dir="$CERT_DIR/$comp"
    apply_secret generic "tls-$comp" \
      --from-file="$dir/tls.crt" --from-file="$dir/tls.key" --from-file="$dir/ca.crt" \
      --from-file="$dir/tls.pk8" --from-file="$dir/keystore.p12" --from-file="$dir/truststore.p12" \
      --from-file="$dir/keystore-password"
  done
  apply_secret tls tls-public --cert="$CERT_DIR/public/tls.crt" --key="$CERT_DIR/public/tls.key"
  apply_secret generic tls-ingress-client \
    --from-file="$CERT_DIR/ingress/tls.crt" --from-file="$CERT_DIR/ingress/tls.key" --from-file="$CERT_DIR/ingress/ca.crt"
  ok "TLS-secrets bijgewerkt"
}

random_secret() { openssl rand -hex 24; }

# Zorgt dat het Secret bestaat en elke sleutel een waarde heeft; bestaande waarden blijven ongewijzigd.
ensure_secret_keys() {
  local secret="$1" key
  shift
  if ! kc -n "$NAMESPACE" get secret "$secret" >/dev/null 2>&1; then
    kc -n "$NAMESPACE" create secret generic "$secret" >/dev/null
  fi
  for key in "$@"; do
    if ! kc -n "$NAMESPACE" get secret "$secret" -o json | jq -e --arg k "$key" '.data[$k] // empty' >/dev/null; then
      kc -n "$NAMESPACE" patch secret "$secret" --type merge \
        -p "{\"data\":{\"$key\":\"$(printf '%s' "$(random_secret)" | base64)\"}}" >/dev/null
    fi
  done
}

create_random_secrets() {
  info "Wachtwoorden en client secrets"
  ensure_secret_keys banksim-postgres superuser-password
  ensure_secret_keys banksim-keycloak admin-password bff-client-secret datagen-client-secret \
    klant-password beheerder-password
  ensure_secret_keys banksim-api cursor-sleutel
  ok "Secrets aanwezig (bestaande waarden blijven ongewijzigd)"
}

certs_checksum() {
  cat "$CERT_DIR"/*/tls.crt | openssl dgst -sha256 | awk '{print substr($NF, 1, 16)}'
}

helm_install() {
  local tag="$1" node_ip
  node_ip="$(kc get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')"
  info "Helm-release $RELEASE installeren (kan enkele minuten duren)"
  helm --kube-context "$KUBE_CONTEXT" upgrade --install "$RELEASE" "$REPO_ROOT/deploy/banksim" \
    --namespace "$NAMESPACE" \
    --set imageTag="$tag" \
    --set certsChecksum="$(certs_checksum)" \
    --set-json "network.probeSources=[\"$node_ip/32\"]" \
    --wait --timeout 15m >/dev/null
  ok "Release $RELEASE geïnstalleerd (image-tag $tag)"
}

expect_status() {
  local url="$1" expected="$2" actual
  actual="$(curl -s -o /dev/null -w '%{http_code}' --cacert "$CERT_DIR/ca.crt" --max-time 10 "$url" || true)"
  [[ "$actual" == "$expected" ]] || fail "$url gaf $actual, verwacht $expected"
  ok "$url → $expected"
}

# Draait curl in een tijdelijke pod en geeft de exitcode van curl terug.
curl_in_pod() {
  local ns="$1" labels="$2" url="$3"
  kc -n "$ns" run "smoke-$RANDOM" --rm -i --restart=Never --quiet \
    --image=curlimages/curl:8.22.0 --labels="$labels" \
    --overrides='{"spec":{"automountServiceAccountToken":false,"securityContext":{"runAsNonRoot":true,"runAsUser":100,"seccompProfile":{"type":"RuntimeDefault"}},"containers":[{"name":"smoke","image":"curlimages/curl:8.22.0","command":["sh","-c","curl -sk --max-time 5 -o /dev/null '"$url"'; echo exit=$?"],"securityContext":{"allowPrivilegeEscalation":false,"capabilities":{"drop":["ALL"]}}}]}}' \
    2>/dev/null | sed -n 's/^exit=//p'
}

smoke_tests() {
  info "Rooktests"
  expect_status "https://bank.localtest.me/" 200
  expect_status "https://bank.localtest.me/api/me" 401
  expect_status "https://auth.localtest.me/realms/banksim/.well-known/openid-configuration" 200
  expect_status "https://auth.localtest.me/admin/" 404

  local code
  code="$(curl_in_pod default "banksim-smoke=netpol" "https://bank-api.$NAMESPACE.svc:8443/api/me")"
  [[ "$code" == "28" ]] || fail "NetworkPolicy: pod buiten de namespace bereikte bank-api (curl exit $code, verwacht timeout 28)"
  ok "NetworkPolicy: pod buiten de namespace kan bank-api niet bereiken"

  # Pod met het label van bank-bff (mag via het netwerk) maar zonder clientcertificaat.
  code="$(curl_in_pod "$NAMESPACE" "app.kubernetes.io/name=bank-bff,banksim-smoke=mtls" "https://bank-api.$NAMESPACE.svc:8443/api/me")"
  [[ "$code" != "0" && "$code" != "28" && -n "$code" ]] \
    || fail "mTLS: bank-api accepteerde een verbinding zonder clientcertificaat (curl exit $code)"
  ok "mTLS: bank-api weigert een aanroep zonder clientcertificaat (curl exit $code)"

  smoke_login
}

# Volledige OIDC-login met klant jdevries via curl, daarna /api/me op alle BFF-replica's.
smoke_login() {
  local password page action body i
  COOKIE_JAR="$(mktemp)"
  local jar="$COOKIE_JAR"
  password="$(kc -n "$NAMESPACE" get secret banksim-keycloak -o jsonpath='{.data.klant-password}' | base64 -d)"
  page="$(curl -s -L --cacert "$CERT_DIR/ca.crt" -b "$jar" -c "$jar" "https://bank.localtest.me/oauth2/authorization/keycloak")"
  action="$(printf '%s' "$page" | sed -n 's/.*id="kc-form-login"[^>]*action="\([^"]*\)".*/\1/p' | sed 's/&amp;/\&/g')"
  [[ -n "$action" ]] || fail "Login: Keycloak-inlogformulier niet gevonden"
  curl -s -L -o /dev/null --cacert "$CERT_DIR/ca.crt" -b "$jar" -c "$jar" \
    --data-urlencode "username=jdevries" --data-urlencode "password=$password" "$action"
  for i in 1 2 3 4 5 6; do
    body="$(curl -s --cacert "$CERT_DIR/ca.crt" -b "$jar" "https://bank.localtest.me/api/me")"
    [[ "$body" == *'"naam":"Jan de Vries"'* ]] || fail "Login: /api/me gaf bij poging $i: ${body:-(leeg/redirect)}"
  done
  body="$(curl -s --cacert "$CERT_DIR/ca.crt" -b "$jar" "https://bank.localtest.me/api/me/accounts")"
  [[ "$body" == *'"soort":"BETAAL"'* && "$body" == *'"soort":"SPAAR"'* ]] \
    || fail "Login: /api/me/accounts gaf ${body:-(leeg)}"
  ok "Login via Keycloak en /api/me werken op alle BFF-replica's"
}

summary() {
  cat <<INFO

BankSim draait op https://bank.localtest.me (Keycloak: https://auth.localtest.me).
Laat je browser de BankSim-CA vertrouwen met: deploy/scripts/trust-ca.sh

Klanten: jdevries, sbakker, melamrani, ljansen, pvisser, fyilmaz, dsmit, edeboer, rmulder, nhendriks
Beheerder: beheerder
Wachtwoorden ophalen met:
  kubectl --context $KUBE_CONTEXT -n $NAMESPACE get secret banksim-keycloak -o jsonpath='{.data.klant-password}' | base64 -d; echo
  kubectl --context $KUBE_CONTEXT -n $NAMESPACE get secret banksim-keycloak -o jsonpath='{.data.beheerder-password}' | base64 -d; echo
INFO
}

COOKIE_JAR=""
cleanup() { [[ -z "$COOKIE_JAR" ]] || rm -f "$COOKIE_JAR"; }
trap cleanup EXIT

main() {
  check_prerequisites
  local tag
  if [[ "$skip_build" == true ]]; then
    tag="$(helm --kube-context "$KUBE_CONTEXT" -n "$NAMESPACE" get values "$RELEASE" -o json 2>/dev/null \
      | sed -n 's/.*"imageTag":"\([^"]*\)".*/\1/p')"
    [[ -n "$tag" ]] || fail "--skip-build vereist een bestaande installatie"
  else
    tag="$(image_tag)"
    build_images "$tag"
  fi
  create_namespace
  create_tls_secrets
  create_random_secrets
  helm_install "$tag"
  if [[ "$smoke_tests" == true ]]; then
    smoke_tests
  fi
  summary
}

main
