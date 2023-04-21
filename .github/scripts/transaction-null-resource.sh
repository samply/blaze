#!/bin/bash -e

#
# This script posts an invalid transaction bundle with a null resource.
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
      "resource": null
    }
  ]
}
END
}
RESULT=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" "$BASE")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "OperationOutcome"
test "severity" "$(echo "$RESULT" | jq -r '.issue[0].severity')" "error"
test "code" "$(echo "$RESULT" | jq -r '.issue[0].code')" "invariant"
test "diagnostics" "$(echo "$RESULT" | jq -r '.issue[0].diagnostics')" 'Error on value `null`. Expected type is `Resource`.'
test "expression" "$(echo "$RESULT" | jq -r '.issue[0].expression[0]')" "entry[0].resource"
