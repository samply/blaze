#!/bin/bash -e

#
# This script fetches the Patient/0 expecting that the resource content
# to be missing.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

result=$(curl -sH "Accept: application/fhir+json" "$base/Patient/0")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$result" | jq -r '.issue[0].severity')" "error"
test "code" "$(echo "$result" | jq -r '.issue[0].code')" "incomplete"
test "diagnostics" "$(echo "$result" | jq -r '.issue[0].diagnostics')" "The resource content of \`Patient/0\` with hash \`C9ADE22457D5AD750735B6B166E3CE8D6878D09B64C2C2868DCB6DE4C9EFBD4F\` was not found."
