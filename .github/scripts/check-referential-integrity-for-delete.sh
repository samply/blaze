#!/bin/bash -e

# Creates a Patient and an Observation referring to this Patient. After that
# tries to delete the Patient. The status code of the delete response can be
# given as first argument. It should be 409 is referential integrity is checked
# and 204 if not.

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
OBSERVATION_ID=$(uuidgen | tr '[:upper:]' '[:lower:]') 

curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"$PATIENT_ID\"}" -o /dev/null "$BASE/Patient/$PATIENT_ID"
curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Observation\", \"id\": \"$OBSERVATION_ID\", \"subject\": {\"reference\": \"Patient/$PATIENT_ID\"}}" -o /dev/null "$BASE/Observation/$OBSERVATION_ID"
RESPONSE_CODE=$(curl -sXDELETE -w '%{response_code}' -o /dev/null "$BASE/Patient/$PATIENT_ID")

test "delete response" "$RESPONSE_CODE" "$1"
