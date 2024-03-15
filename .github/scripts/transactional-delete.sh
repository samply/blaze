#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
OBSERVATION_ID=$(uuidgen | tr '[:upper:]' '[:lower:]') 

curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"$PATIENT_ID\"}" -o /dev/null "$BASE/Patient/$PATIENT_ID"
curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Observation\", \"id\": \"$OBSERVATION_ID\", \"subject\": {\"reference\": \"Patient/$PATIENT_ID\"}}" -o /dev/null "$BASE/Observation/$OBSERVATION_ID"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "request": {
        "method": "DELETE",
        "url": "Patient/$PATIENT_ID"
      }
    },
    {
      "request": {
        "method": "DELETE",
        "url": "Observation/$OBSERVATION_ID"
      }
    }
  ]
}
END
}
RESPONSE=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" "$BASE")

test "delete response for the first entry" "$(echo "$RESPONSE" | jq -r '.entry[0].response.status')" "204"
test "delete response for the second entry" "$(echo "$RESPONSE" | jq -r '.entry[1].response.status')" "204"
