#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../util.sh"

type="$1"

base="http://localhost:8080/fhir"
result=$(curl -sXDELETE -H "Prefer: return=OperationOutcome" "$base/$type")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$result" | jq -r .issue[0].severity)" "success"
test "code" "$(echo "$result" | jq -r .issue[0].code)" "success"
