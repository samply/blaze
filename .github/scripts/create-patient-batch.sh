#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient"
      },
      "request": {
        "method": "POST",
        "url": "Patient"
      }
    }
  ]
}
END
}
result=$(curl -sH "Content-Type: application/fhir+json" -H "Prefer: return=representation" -d "$(bundle)" "$base")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "Bundle"
test "bundle type" "$(echo "$result" | jq -r .type)" "batch-response"
test "response status" "$(echo "$result" | jq -r .entry[].response.status)" "201"
test "response resource type" "$(echo "$result" | jq -r .entry[].resource.resourceType)" "Patient"
test_regex "response resource ID" "$(echo "$result" | jq -r .entry[].resource.id)" "^[A-Z0-9]{16}$"
