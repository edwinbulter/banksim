#!/usr/bin/env bash
# Maakt de BankSim-CA en per component een certificaat voor mTLS (technisch ontwerp §10.1).
#
#   certs.sh            maakt ontbrekende of bijna verlopen certificaten aan
#   certs.sh --rotate   vernieuwt alle componentcertificaten (de CA blijft)
#
# Uitvoer: deploy/.secrets/certs/<component>/ met tls.crt, tls.key, ca.crt,
# tls.pk8 (DER, voor de PostgreSQL JDBC-driver), keystore.p12, truststore.p12 en keystore-password.
# De CA-sleutel verlaat deze map nooit.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="${BANKSIM_SECRETS_DIR:-$SCRIPT_DIR/../.secrets}/certs"
NAMESPACE="${BANKSIM_NAMESPACE:-banksim}"
CA_DAYS=825
CERT_DAYS=90
RENEW_BEFORE_DAYS=30

# Componenten met hun extra SAN's. Elk certificaat krijgt ook <naam>.<namespace>.svc(.cluster.local).
# "public" is het servercertificaat voor de publieke hosts op ingress-nginx; "ingress" is het
# clientcertificaat waarmee ingress-nginx zich bij web, bff en Keycloak meldt.
COMPONENTS=(
  "public:bank.localtest.me,auth.localtest.me"
  "ingress:"
  "bank-web:"
  "bank-bff:"
  "bank-api:"
  "bank-migrate:"
  "bank-datagen:"
  "keycloak:"
  "postgres:"
)

rotate=false
for arg in "$@"; do
  case "$arg" in
    --rotate) rotate=true ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "Onbekende optie: $arg" >&2; exit 1 ;;
  esac
done

command -v openssl >/dev/null || { echo "openssl ontbreekt" >&2; exit 1; }
command -v keytool >/dev/null || { echo "keytool (JDK) ontbreekt" >&2; exit 1; }

umask 077
mkdir -p "$CERT_DIR"

ca_key="$CERT_DIR/ca.key"
ca_crt="$CERT_DIR/ca.crt"

if [[ ! -f "$ca_key" || ! -f "$ca_crt" ]]; then
  echo "BankSim-CA aanmaken"
  openssl genrsa -out "$ca_key" 3072 2>/dev/null
  openssl req -x509 -new -key "$ca_key" -sha256 -days "$CA_DAYS" \
    -subj "/CN=BankSim Local CA" -out "$ca_crt" \
    -extensions v3_ca -config <(cat <<'EOF'
[req]
distinguished_name = dn
[dn]
[v3_ca]
basicConstraints = critical,CA:TRUE,pathlen:0
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash
EOF
)
fi

needs_cert() {
  local crt="$1"
  [[ "$rotate" == true || ! -f "$crt" ]] && return 0
  ! openssl x509 -checkend $((RENEW_BEFORE_DAYS * 86400)) -noout -in "$crt" >/dev/null
}

for entry in "${COMPONENTS[@]}"; do
  name="${entry%%:*}"
  extra="${entry#*:}"
  dir="$CERT_DIR/$name"
  mkdir -p "$dir"

  if ! needs_cert "$dir/tls.crt"; then
    continue
  fi
  echo "Certificaat aanmaken: $name"

  sans="DNS:$name,DNS:$name.$NAMESPACE.svc,DNS:$name.$NAMESPACE.svc.cluster.local"
  if [[ -n "$extra" ]]; then
    IFS=',' read -ra hosts <<< "$extra"
    for h in "${hosts[@]}"; do sans="$sans,DNS:$h"; done
  fi

  openssl genrsa -out "$dir/tls.key" 2048 2>/dev/null
  openssl req -new -key "$dir/tls.key" -subj "/CN=$name" -out "$dir/tls.csr"
  openssl x509 -req -in "$dir/tls.csr" -CA "$ca_crt" -CAkey "$ca_key" -CAcreateserial \
    -CAserial "$CERT_DIR/ca.srl" -days "$CERT_DAYS" -sha256 -out "$dir/tls.crt" \
    -extfile <(cat <<EOF
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth,clientAuth
subjectAltName = $sans
authorityKeyIdentifier = keyid
EOF
) 2>/dev/null
  rm -f "$dir/tls.csr"
  cp "$ca_crt" "$dir/ca.crt"

  # PKCS#8 DER-sleutel voor de PostgreSQL JDBC-driver (sslkey)
  openssl pkcs8 -topk8 -inform PEM -outform DER -nocrypt -in "$dir/tls.key" -out "$dir/tls.pk8"

  # PKCS#12 key- en truststore voor Java-clients en Keycloak
  password="$(openssl rand -hex 24)"
  printf '%s' "$password" > "$dir/keystore-password"
  openssl pkcs12 -export -name "$name" -inkey "$dir/tls.key" -in "$dir/tls.crt" -certfile "$ca_crt" \
    -passout "pass:$password" -out "$dir/keystore.p12"
  rm -f "$dir/truststore.p12"
  keytool -importcert -noprompt -alias banksim-ca -file "$ca_crt" \
    -keystore "$dir/truststore.p12" -storetype PKCS12 -storepass "$password" >/dev/null 2>&1
done

echo "Certificaten staan in $CERT_DIR"
