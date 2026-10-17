#!/bin/bash
set -euo pipefail

#
# This script verifies that a type-level search with an invalid _id value
# results in a 400 response with an OperationOutcome naming the search param.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

# the invalid id is `Ł` (URL-encoded as %C5%81)
response=$(curl -s -w '%{response_code}' -H 'Accept: application/fhir+json' "$base/Patient?_id=%C5%81")
test "status code" "${response: -3}" "400"
test "resource type" "$(echo "${response%???}" | jq -r '.resourceType')" "OperationOutcome"
test "issue code" "$(echo "${response%???}" | jq -r '.issue[0].code')" "invalid"
test "diagnostics" "$(echo "${response%???}" | jq -r '.issue[0].diagnostics')" "Error in search parameter \`_id\`: Invalid resource id \`Ł\`."
