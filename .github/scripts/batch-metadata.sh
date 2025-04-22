#!/bin/bash -e

#
# This script fetches the CapabilityStatement through a batch request.
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "request": {
        "method": "GET",
        "url": "metadata"
      }
    }
  ]
}
END
}
RESULT=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" "$BASE")

test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "Bundle"
test "bundle type" "$(echo "$RESULT" | jq -r .type)" "batch-response"
test "response status" "$(echo "$RESULT" | jq -r .entry[].response.status)" "200"
test "response resource type" "$(echo "$RESULT" | jq -r .entry[].resource.resourceType)" "CapabilityStatement"
