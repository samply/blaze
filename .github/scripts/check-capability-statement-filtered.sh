#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
CAPABILITY_STATEMENT=$(curl -sH 'Accept: application/fhir+json' "$BASE/metadata?_elements=status,software")

test "list of found keys" "$(echo "$CAPABILITY_STATEMENT" | jq -c 'keys')" "[\"resourceType\",\"software\",\"status\"]"
