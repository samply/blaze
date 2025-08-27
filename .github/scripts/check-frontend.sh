#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

CA_CERT_BLAZE="$SCRIPT_DIR/../../modules/ingress/blaze-cert.pem"
CA_CERT_KEYCLOAK="$SCRIPT_DIR/../../modules/ingress/keycloak-cert.pem"
ACCESS_TOKEN=$(curl -s -d 'grant_type=client_credentials' --cacert "$CA_CERT_KEYCLOAK" -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f "https://keycloak.localhost/realms/blaze/protocol/openid-connect/token" | jq -r .access_token)

if [ -z "$ACCESS_TOKEN" ]; then
  echo "Missing access token"
  exit 1;
fi

BASE="https://blaze.localhost/fhir"

echo "checking index.html using the Accept header..."
STATUS_CODE=$(curl -sL --cacert "$CA_CERT_BLAZE" -H 'Accept: text/html' -o /dev/null -w '%{response_code}' "$BASE")
HEADERS=$(curl -sL --cacert "$CA_CERT_BLAZE" -H 'Accept: text/html' -o /dev/null -D - "$BASE")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$STATUS_CODE" "200"
test "Content-Type header" "$CONTENT_TYPE_HEADER" "text/html"

echo "checking index.html using the _format query param..."
STATUS_CODE=$(curl -sL --cacert "$CA_CERT_BLAZE" -o /dev/null -w '%{response_code}' "$BASE?_format=html")
HEADERS=$(curl -sL --cacert "$CA_CERT_BLAZE" -o /dev/null -D - "$BASE?_format=html")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$STATUS_CODE" "200"
test "Content-Type header" "$CONTENT_TYPE_HEADER" "text/html"

echo "checking version.json..."
STATUS_CODE=$(curl -sL --cacert "$CA_CERT_BLAZE" -H 'Accept: text/html' -o /dev/null -w '%{response_code}' "$BASE/_app/version.json")
HEADERS=$(curl -sL --cacert "$CA_CERT_BLAZE" -H 'Accept: text/html' -o /dev/null -D - "$BASE/_app/version.json")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$STATUS_CODE" "200"
test "Content-Type header" "$CONTENT_TYPE_HEADER" "application/json"

echo "checking system-search using access token..."
RESOURCE_TYPE=$(curl -sL --cacert "$CA_CERT_BLAZE" --oauth2-bearer "$ACCESS_TOKEN" "$BASE" | jq -r .resourceType)

test "Resource type" "$RESOURCE_TYPE" "Bundle"

echo "checking system-search JSON using the _format query param..."
STATUS_CODE=$(curl -sL --cacert "$CA_CERT_BLAZE" --oauth2-bearer "$ACCESS_TOKEN" -o /dev/null -w '%{response_code}' "$BASE?_format=json")
HEADERS=$(curl -sL --cacert "$CA_CERT_BLAZE" --oauth2-bearer "$ACCESS_TOKEN" -o /dev/null -D - "$BASE?_format=json")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$STATUS_CODE" "200"
test "Content-Type header" "$CONTENT_TYPE_HEADER" "application/fhir+json;charset=utf-8"

echo "checking system-search XML using the _format query param..."
STATUS_CODE=$(curl -sL --cacert "$CA_CERT_BLAZE" --oauth2-bearer "$ACCESS_TOKEN" -o /dev/null -w '%{response_code}' "$BASE?_format=xml")
HEADERS=$(curl -sL --cacert "$CA_CERT_BLAZE" --oauth2-bearer "$ACCESS_TOKEN" -o /dev/null -D - "$BASE?_format=xml")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$STATUS_CODE" "200"
test "Content-Type header" "$CONTENT_TYPE_HEADER" "application/fhir+xml;charset=utf-8"
