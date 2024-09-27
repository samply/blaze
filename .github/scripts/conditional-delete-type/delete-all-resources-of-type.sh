#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

TYPE="$1"

BASE="http://localhost:8080/fhir"
RESULT=$(curl -sXDELETE -H "Prefer: return=OperationOutcome" "$BASE/$TYPE")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$RESULT" | jq -r .issue[0].severity)" "success"
test "code" "$(echo "$RESULT" | jq -r .issue[0].code)" "success"
