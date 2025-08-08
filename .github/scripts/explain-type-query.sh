#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TYPE=$1
QUERY="${2//[[:space:]]/}"
EXPECTED_PLAN=$3
ACTUAL_PLAN=$(curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$BASE/$TYPE?$QUERY&__explain=true" | jq -r '.entry[0].resource.issue[0].diagnostics')

test "plan" "$ACTUAL_PLAN" "$EXPECTED_PLAN"
