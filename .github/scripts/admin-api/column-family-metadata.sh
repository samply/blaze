#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

CA_CERT_BLAZE="$SCRIPT_DIR/../../../modules/ingress/blaze-cert.pem"
CA_CERT_KEYCLOAK="$SCRIPT_DIR/../../../modules/ingress/keycloak-cert.pem"
ACCESS_TOKEN=$(curl -s -d 'grant_type=client_credentials' --cacert "$CA_CERT_KEYCLOAK" -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f "https://keycloak.localhost/realms/blaze/protocol/openid-connect/token" | jq -r .access_token)

if [ -z "$ACCESS_TOKEN" ]; then
  echo "Missing access token"
  exit 1;
fi

BASE="https://blaze.localhost/fhir"
COLUMN_FAMILY=$1
ACTUAL_NAME=$(curl -sL --cacert "$CA_CERT_BLAZE" --oauth2-bearer "$ACCESS_TOKEN" "$BASE/__admin/dbs/index/column-families/$COLUMN_FAMILY/metadata" | jq -r .name)

test "column family name" "$ACTUAL_NAME" "$COLUMN_FAMILY"
