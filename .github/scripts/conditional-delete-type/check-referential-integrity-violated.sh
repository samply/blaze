#!/bin/bash -e
set -o pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../util.sh"

base="http://localhost:8080/fhir"
result=$(curl -s -XDELETE "$base/Patient?identifier=S99996194")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$result" | jq -r .issue[0].severity)" "error"
test "code" "$(echo "$result" | jq -r .issue[0].code)" "conflict"
test_regex "diagnostics" "$(echo "$result" | jq -r .issue[0].diagnostics)" "^Referential integrity violated.*"
