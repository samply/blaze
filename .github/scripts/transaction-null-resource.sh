#!/bin/bash -e

#
# This script posts an invalid transaction bundle with a null resource.
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
      "resource": null
    }
  ]
}
END
}
result=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" "$base")

test "resource type" "$(echo "$result" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$result" | jq -r '.issue[0].severity')" "error"
test "code" "$(echo "$result" | jq -r '.issue[0].code')" "invariant"
test "diagnostics" "$(echo "$result" | jq -r '.issue[0].diagnostics')" "Error on value null. Expected type is \`Resource\`."
test "expression" "$(echo "$result" | jq -r '.issue[0].expression[0]')" "Bundle.entry[0].resource"
