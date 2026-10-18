#!/bin/bash
set -euo pipefail

#
# This script verifies that instance-level requests with an invalid resource id
# result in a 400 response with an OperationOutcome having the code
# MSG_ID_INVALID.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
too_long_id=$(printf 'a%.0s' {1..65})

test_invalid_id() {
  local method="$1"
  local path="$2"
  local id="$3"
  local response
  response=$(curl -s -w '%{response_code}' -X "$method" -H 'Accept: application/fhir+json' "${@:4}" "$base/$path")
  local body="${response%???}"

  echo "$method $path"
  test "status code" "${response: -3}" "400"
  test "resource type" "$(echo "$body" | jq -r '.resourceType')" "OperationOutcome"
  test "issue code" "$(echo "$body" | jq -r '.issue[0].code')" "value"
  test "operation outcome code" "$(echo "$body" | jq -r '.issue[0].details.coding[0].code')" "MSG_ID_INVALID"
  test "diagnostics" "$(echo "$body" | jq -r '.issue[0].diagnostics')" "Resource id \`$id\` is invalid."
}

test_invalid_id GET "Patient/$too_long_id" "$too_long_id"
test_invalid_id GET "Patient/A_B" "A_B"
test_invalid_id PUT "Patient/$too_long_id" "$too_long_id" \
  -H 'Content-Type: application/fhir+json' \
  -d "{\"resourceType\": \"Patient\", \"id\": \"$too_long_id\"}"
test_invalid_id DELETE "Patient/$too_long_id" "$too_long_id"
test_invalid_id GET "Patient/$too_long_id/_history" "$too_long_id"
test_invalid_id GET "Patient/A_B/_history" "A_B"
test_invalid_id GET "Patient/$too_long_id/\$everything" "$too_long_id"
test_invalid_id GET "Patient/A_B/Observation" "A_B"
