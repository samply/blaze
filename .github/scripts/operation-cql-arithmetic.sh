#!/bin/bash
set -euo pipefail

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

result="$(parameters | execute_operation "$base/\$cql")"

test "result resource type" "$(echo "$result" | jq -r '.resourceType')" "Parameters"
test "result value" "$(echo "$result" | jq -r '.parameter[0].valueInteger')" "2"
