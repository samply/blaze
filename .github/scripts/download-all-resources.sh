#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
EXPECTED_SIZE_JSON=$(curl -sH 'Accept: application/fhir+json' "$BASE?_summary=count" | jq -r .total)
EXPECTED_SIZE_XML=$(curl -sH 'Accept: application/fhir+xml' "$BASE?_summary=count" | xq -x /Bundle/total/@value)
ACTUAL_SIZE=$(blazectl --server "$BASE" download 2>/dev/null | wc -l | xargs)

test "totals (JSON, XML) match" "$EXPECTED_SIZE_JSON" "$EXPECTED_SIZE_XML"
test "download size" "$ACTUAL_SIZE" "$EXPECTED_SIZE_JSON"
