#!/bin/bash -e

#
# This script first creates a patient and expects the conditional update with
# If-None-Match=* to fail afterwards.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_id=$(curl -sH "Content-Type: application/fhir+json" \
  -d '{"resourceType": "Patient"}' "$base/Patient" | jq -r .id)

patient="{\"resourceType\": \"Patient\", \"id\": \"$patient_id\"}"
result=$(curl -sXPUT -H "Content-Type: application/fhir+json" -H "If-None-Match: *" \
  -d "$patient" "$base/Patient/$patient_id")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$result" | jq -r .issue[0].severity)" "error"
test "code" "$(echo "$result" | jq -r .issue[0].code)" "conflict"
test "diagnostics" "$(echo "$result" | jq -r .issue[0].diagnostics)" "Resource \`Patient/$patient_id\` already exists."
