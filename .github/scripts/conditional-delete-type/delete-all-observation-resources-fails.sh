#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../util.sh"

base="http://localhost:8080/fhir"
result=$(curl -sXDELETE -H "Prefer: return=OperationOutcome" "$base/Observation")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$result" | jq -r .issue[0].severity)" "error"
test "code" "$(echo "$result" | jq -r .issue[0].code)" "too-costly"
test "diagnostics" "$(echo "$result" | jq -r .issue[0].diagnostics)" "Conditional delete of all Observations failed because more than 10,000 matches were found."
