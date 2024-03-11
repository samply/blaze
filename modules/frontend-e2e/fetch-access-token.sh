#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
CA_CERT="$SCRIPT_DIR/../ingress/keycloak-cert.pem"
REALM_BASE="https://keycloak.localhost/realms/blaze"

CLIENT_ID=${1:-account}
CLIENT_SECRET=${2:-e11a3a8e-6e24-4f9d-b914-da7619e8b31f}

RESPONSE="$(curl -s -d 'grant_type=client_credentials' --cacert "$CA_CERT" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  "$REALM_BASE/protocol/openid-connect/token")"

if [ -n "$(echo "$RESPONSE" | jq -r '.error // empty')" ]; then
  echo "Error: $(echo "$RESPONSE" | jq -r .error_description)"
  exit 1
fi

echo "$RESPONSE" | jq -r .access_token
