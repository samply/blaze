#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_ID=$(curl -s "$BASE/Patient?identifier=http://hl7.org/fhir/sid/us-ssn|999-82-5655" | jq -r '.entry[].resource.id')
OBSERVATION_COUNT=$(curl -s "$BASE/Patient/$PATIENT_ID/Observation?_summary=count" | jq .total)

test "observation count" "$OBSERVATION_COUNT" "1277"
