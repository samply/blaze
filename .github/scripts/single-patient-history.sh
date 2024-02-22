#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_IDENTIFIER="$1"
PATIENT_ID=$(curl -s "$BASE/Patient?identifier=$PATIENT_IDENTIFIER" | jq -r '.entry[0].resource.id')
FIRST_PAGE=$(curl -s "$BASE/Patient/$PATIENT_ID/_history")
FIRST_PAGE_SIZE=$(echo "$FIRST_PAGE" | jq -r '.entry | length')

test "first page size" "$FIRST_PAGE_SIZE" "1"
