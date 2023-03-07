#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
CAPABILITY_STATEMENT=$(curl -sH 'Accept: application/fhir+json' "$BASE/metadata")

test "software name" "$(echo "$CAPABILITY_STATEMENT" | jq -r .software.name)" "Blaze"
test "URL" "$(echo "$CAPABILITY_STATEMENT" | jq -r .implementation.url)" "http://localhost:8080/fhir"
