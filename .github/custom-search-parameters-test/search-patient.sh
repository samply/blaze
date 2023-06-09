#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../scripts/util.sh"

BASE="http://localhost:8080/fhir"

count() {
  curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$BASE/Patient?marital-status=M" | jq .total
}

test "number of patients" "$(count)" "1"
