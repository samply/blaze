#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
test "total number of resources" "$(curl -sH 'Accept: application/fhir+json' "$BASE" | jq -r .total)" "$1"
