#!/bin/bash -e

# Creates a Patient and an Observation referring to this Patient. After that
# tries to delete the Patient. This delete should result in a 409. After that
# it deleted the referring Observation, which should free up the Patient.
# Finally the script tries to delete the Patient again and expects that request
# to succeed.

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
OBSERVATION_ID=$(uuidgen | tr '[:upper:]' '[:lower:]') 

curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Patient\", \"id\": \"$PATIENT_ID\"}" -o /dev/null "$BASE/Patient/$PATIENT_ID"
curl -s -f -XPUT -H 'Content-Type: application/fhir+json' -d "{\"resourceType\": \"Observation\", \"id\": \"$OBSERVATION_ID\", \"subject\": {\"reference\": \"Patient/$PATIENT_ID\"}}" -o /dev/null "$BASE/Observation/$OBSERVATION_ID"

test "first delete Patient response" "$(curl -sXDELETE -w '%{response_code}' -o /dev/null "$BASE/Patient/$PATIENT_ID")" "409"
test "delete Observation response" "$(curl -sXDELETE -w '%{response_code}' -o /dev/null "$BASE/Observation/$OBSERVATION_ID")" "204"
test "second delete Patient response" "$(curl -sXDELETE -w '%{response_code}' -o /dev/null "$BASE/Patient/$PATIENT_ID")" "204"
