#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

BASE="http://localhost:8080/fhir"
RESULT=$(curl -sXDELETE "$BASE/Patient?identifier=S99996194")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$RESULT" | jq -r .issue[0].severity)" "error"
test "code" "$(echo "$RESULT" | jq -r .issue[0].code)" "conflict"
test_regex "diagnostics" "$(echo "$RESULT" | jq -r .issue[0].diagnostics)" "^Referential integrity violated.*"
