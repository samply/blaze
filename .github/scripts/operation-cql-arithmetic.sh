#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "expression",
      "valueString": "1+1"
    }
  ]
}
END
}

result="$(curl -sfH 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' -d "$(parameters)" "$base/\$cql")"

test "result resource type" "$(echo "$result" | jq -r '.resourceType')" "Parameters"
test "result value" "$(echo "$result" | jq -r '.parameter[0].valueInteger')" "2"
