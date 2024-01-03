#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_IDENTIFIER="X81372825X"
PATIENT_ID=$(curl -s "$BASE/Patient?identifier=$PATIENT_IDENTIFIER" | jq -r '.entry[0].resource.id')
ERROR_CODE=$(curl -s "$BASE/Patient/$PATIENT_ID/\$everything" |  jq -r '.issue[0].code')

test "error code" "$ERROR_CODE" "too-costly"
