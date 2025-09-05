#!/bin/bash -e

#
# This script fetches the Patient/0 expecting that the resource content
# to be missing.
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

RESULT=$(curl -sH "Accept: application/fhir+json" "$BASE/Patient/0")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$RESULT" | jq -r '.issue[0].severity')" "error"
test "code" "$(echo "$RESULT" | jq -r '.issue[0].code')" "incomplete"
test "diagnostics" "$(echo "$RESULT" | jq -r '.issue[0].diagnostics')" "The resource content of \`Patient/0\` with hash \`5EE37C94FB1626111B5C2D37F7C2ECAF21B50B9D0FB45FA189889F38D0F9A470\` was not found."
