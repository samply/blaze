#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

BASE="http://localhost:8080/fhir"
RESULT=$(curl -sXDELETE -H "Prefer: return=OperationOutcome" "$BASE/Provenance")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$RESULT" | jq -r .issue[0].severity)" "success"
test "code" "$(echo "$RESULT" | jq -r .issue[0].code)" "success"
test "diagnostics" "$(echo "$RESULT" | jq -r .issue[0].diagnostics)" "Successfully deleted 119 Provenances."
