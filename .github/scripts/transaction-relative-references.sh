#!/bin/bash -e

#
# This script executes a transaction with relative references that are resolved
# against its RESTful base.
#
# See: https://hl7.org/fhir/bundle.html#references
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "fullUrl": "http://example.com/fhir/Patient/0",
      "resource": {
        "resourceType": "Patient"
      },
      "request": {
        "method": "POST",
        "url": "Patient"
      }
    },
    {
      "fullUrl": "http://example.com/fhir/Observation/0",
      "resource": {
        "resourceType": "Observation",
        "subject": {
          "reference": "Patient/0"
        }
      },
      "request": {
        "method": "POST",
        "url": "Observation"
      }
    }
  ]
}
END
}
result=$(bundle | transact "$base")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "Bundle"
test "bundle type" "$(echo "$result" | jq -r .type)" "transaction-response"
test "response status" "$(echo "$result" | jq -r .entry[].response.status | uniq)" "201"
