#!/bin/bash -e

#
# This script tests that an update without changes of the resource content
# doesn't create a new history entry.
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "resource": $1,
      "request": {
        "method": "PUT",
        "url": "Patient/$2"
      }
    }
  ]
}
END
}

BASE="http://localhost:8080/fhir"
PATIENT_IDENTIFIER="X79746011X"
PATIENT=$(curl -sH "Accept: application/fhir+json" "$BASE/Patient?identifier=$PATIENT_IDENTIFIER" | jq -cM '.entry[0].resource')
ID="$(echo "$PATIENT" | jq -r .id)"
VERSION_ID="$(echo "$PATIENT" | jq -r .meta.versionId)"

# Update Interaction
RESULT=$(curl -sXPUT -H "Content-Type: application/fhir+json" -d "$PATIENT" "$BASE/Patient/$ID")
RESULT_VERSION_ID="$(echo "$RESULT" | jq -r .meta.versionId)"

test "update versionId" "$RESULT_VERSION_ID" "$VERSION_ID"

# Transaction Interaction
RESULT=$(curl -sH "Content-Type: application/fhir+json" -H "Prefer: return=representation" -d "$(bundle "$PATIENT" "$ID")" "$BASE")
RESULT_VERSION_ID="$(echo "$RESULT" | jq -r '.entry[0].resource.meta.versionId')"

test "transaction versionId" "$RESULT_VERSION_ID" "$VERSION_ID"

HISTORY_TOTAL=$(curl -sH "Accept: application/fhir+json" "$BASE/Patient/$ID/_history" | jq -r '.total')

test "history total" "$HISTORY_TOTAL" "1"
