#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

BASE="http://localhost:8080/fhir"

ERROR_MESSAGE="$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "{\"resourceType\": \"Task\"}" "$BASE/__admin/Task" | jq -r '.issue[].details.text')"
test "error message" "$ERROR_MESSAGE" "No allowed profile found."

re-index-job() {
cat <<END
{
  "resourceType": "Task",
  "meta": {
    "profile": [
      "https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob"
    ]
  }
}
END
}

ERROR_MESSAGE="$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "$(re-index-job)" "$BASE/__admin/Task" | jq -r '.issue[0].diagnostics')"
test "error message" "$ERROR_MESSAGE" "Task.status: minimum required = 1, but only found 0 (from https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob)"
