#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../util.sh"

BASE="http://localhost:8080/fhir"

prune-job() {
cat <<END
{
  "resourceType": "Task",
  "id": "PruneJobReadyExample",
  "meta": {
    "profile": [
      "https://samply.github.io/blaze/fhir/StructureDefinition/PruneJob"
    ]
  },
  "input": [
    {
      "type": {
        "coding": [
          {
            "code": "t",
            "system": "https://samply.github.io/blaze/fhir/CodeSystem/PruneJobParameter",
            "display": "T"
          }
        ]
      },
      "valuePositiveInt": 2000
    }
  ],
  "code": {
    "coding": [
      {
        "code": "prune",
        "system": "https://samply.github.io/blaze/fhir/CodeSystem/JobType",
        "display": "Prune the Database"
      }
    ]
  },
  "status": "ready",
  "intent": "order",
  "authoredOn": "2024-10-15T15:01:00.000Z"
}
END
}

RESULT="$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "$(prune-job)" "$BASE/__admin/Task")"
test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "Task"
test "status" "$(echo "$RESULT" | jq -r .status)" "ready"

sleep 2

ID="$(echo "$RESULT" | jq -r .id)"
RESULT="$(curl -s -H 'Accept: application/fhir+json' "$BASE/__admin/Task/$ID")"
test "resource type" "$(echo "$RESULT" | jq -r .resourceType)" "Task"
test "status" "$(echo "$RESULT" | jq -r .status)" "completed"
