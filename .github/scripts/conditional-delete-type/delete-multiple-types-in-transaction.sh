#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../util.sh"

base="http://localhost:8080/fhir"

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
result=$(curl -sfH "Content-Type: application/fhir+json" -d "$(bundle)" "$base")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "Bundle"
test "bundle type" "$(echo "$result" | jq -r .type)" "transaction-response"
test "bundle entry response status" "$(echo "$result" | jq -r .entry[].response.status | uniq)" "204"
