#!/bin/bash -e

#
# This script creates and reads a patient in a single transaction.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_id="e42a47bb-a371-4cf5-9f17-51e59c1f612a"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "request": {
        "method": "PUT",
        "url": "Patient/$patient_id"
      },
      "resource": {
        "resourceType": "Patient",
        "id": "$patient_id"
      }
    },
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
test "response status" "$(echo "$result" | jq -r .entry[1].response.status)" "200"
test "patient id" "$(echo "$result" | jq -r .entry[1].resource.id)" "$patient_id"
