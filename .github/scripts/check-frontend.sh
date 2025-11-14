#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

ca_cert_blaze="$script_dir/../../modules/ingress/blaze-cert.pem"
ca_cert_keycloak="$script_dir/../../modules/ingress/keycloak-cert.pem"
access_token=$(curl -s -d 'grant_type=client_credentials' --cacert "$ca_cert_keycloak" -u account:e11a3a8e-6e24-4f9d-b914-da7619e8b31f "https://keycloak.localhost/realms/blaze/protocol/openid-connect/token" | jq -r .access_token)

if [ -z "$access_token" ]; then
  echo "Missing access token"
  exit 1;
fi

base="https://blaze.localhost/fhir"

echo "checking index.html using the Accept header..."
status_code=$(curl -sL --cacert "$ca_cert_blaze" -H 'Accept: text/html' -o /dev/null -w '%{response_code}' "$base")
headers=$(curl -sL --cacert "$ca_cert_blaze" -H 'Accept: text/html' -o /dev/null -D - "$base")
content_type_header=$(echo "$headers" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$status_code" "200"
test "Content-Type header" "$content_type_header" "text/html"

echo "checking index.html using the _format query param..."
status_code=$(curl -sL --cacert "$ca_cert_blaze" -o /dev/null -w '%{response_code}' "$base?_format=html")
headers=$(curl -sL --cacert "$ca_cert_blaze" -o /dev/null -D - "$base?_format=html")
content_type_header=$(echo "$headers" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$status_code" "200"
test "Content-Type header" "$content_type_header" "text/html"

echo "checking version.json..."
status_code=$(curl -sL --cacert "$ca_cert_blaze" -H 'Accept: text/html' -o /dev/null -w '%{response_code}' "$base/_app/version.json")
headers=$(curl -sL --cacert "$ca_cert_blaze" -H 'Accept: text/html' -o /dev/null -D - "$base/_app/version.json")
content_type_header=$(echo "$headers" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$status_code" "200"
test "Content-Type header" "$content_type_header" "application/json"

echo "checking system-search using access token..."
resource_type=$(curl -sL --cacert "$ca_cert_blaze" --oauth2-bearer "$access_token" "$base" | jq -r .resourceType)

test "Resource type" "$resource_type" "Bundle"

echo "checking system-search JSON using the _format query param..."
status_code=$(curl -sL --cacert "$ca_cert_blaze" --oauth2-bearer "$access_token" -o /dev/null -w '%{response_code}' "$base?_format=json")
headers=$(curl -sL --cacert "$ca_cert_blaze" --oauth2-bearer "$access_token" -o /dev/null -D - "$base?_format=json")
content_type_header=$(echo "$headers" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$status_code" "200"
test "Content-Type header" "$content_type_header" "application/fhir+json;charset=utf-8"

echo "checking system-search XML using the _format query param..."
status_code=$(curl -sL --cacert "$ca_cert_blaze" --oauth2-bearer "$access_token" -o /dev/null -w '%{response_code}' "$base?_format=xml")
headers=$(curl -sL --cacert "$ca_cert_blaze" --oauth2-bearer "$access_token" -o /dev/null -D - "$base?_format=xml")
content_type_header=$(echo "$headers" | grep -iv 'X-Content-Type-Options' | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "status code" "$status_code" "200"
test "Content-Type header" "$content_type_header" "application/fhir+xml;charset=utf-8"

echo "check Encounter supported profile..."
capability_statement=$(curl -sL --cacert "$ca_cert_blaze" --oauth2-bearer "$access_token" -H 'Accept: application/fhir+json' "$base/metadata")
test "Encounter Supported Profile" "$(echo "$capability_statement" | jq -r '.rest[0].resource[] | select(.type == "Encounter") .supportedProfile[0]')" "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung|2025.0.0"
