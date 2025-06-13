#!/bin/bash -e

#
# This script executes a transaction with relative references that are resolved
# against its RESTful base.
#
# See: https://hl7.org/fhir/bundle.html#references
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

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
RESULT=$(bundle | transact "$BASE")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "Bundle"
test "bundle type" "$(echo "$RESULT" | jq -r .type)" "transaction-response"
test "response status" "$(echo "$RESULT" | jq -r .entry[].response.status | uniq)" "201"
