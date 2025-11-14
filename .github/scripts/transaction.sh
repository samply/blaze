#!/bin/bash -e

#
# This script creates a patient and tries to retrieve it through a transaction
# request.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_id=$(curl -sH "Content-Type: application/fhir+json" \
  -d '{"resourceType": "Patient"}' "$base/Patient" | jq -r .id)

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "request": {
        "method": "GET",
        "url": "Patient/$patient_id"
      }
    }
  ]
}
END
}
result=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" "$base")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "Bundle"
test "bundle type" "$(echo "$result" | jq -r .type)" "transaction-response"
test "response status" "$(echo "$result" | jq -r .entry[].response.status)" "200"
test "patient id" "$(echo "$result" | jq -r .entry[].resource.id)" "$patient_id"
