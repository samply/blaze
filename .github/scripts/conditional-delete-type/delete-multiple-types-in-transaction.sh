#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

BASE="http://localhost:8080/fhir"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "request": {
        "method": "DELETE",
        "url": "Condition"
      }
    },
    {
      "request": {
        "method": "DELETE",
        "url": "Provenance"
      }
    },
    {
      "request": {
        "method": "DELETE",
        "url": "CarePlan"
      }
    },
    {
      "request": {
        "method": "DELETE",
        "url": "Procedure"
      }
    }
  ]
}
END
}
RESULT=$(curl -sfH "Content-Type: application/fhir+json" -d "$(bundle)" "$BASE")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "Bundle"
test "bundle type" "$(echo "$RESULT" | jq -r .type)" "transaction-response"
test "bundle entry response status" "$(echo "$RESULT" | jq -r .entry[].response.status | uniq)" "204"
