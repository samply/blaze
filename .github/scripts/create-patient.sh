#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
HEADERS=$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "{\"resourceType\": \"Patient\"}" -o /dev/null -D - "$BASE/Patient")
LOCATION_HEADER=$(echo "$HEADERS" | grep -i location | tr -d '\r')

test "Location header prefix" "$(echo "\"${LOCATION_HEADER:10}\"" | jq -r '[split("/") | limit(5;.[])] | join("/")')" "$BASE/Patient"
