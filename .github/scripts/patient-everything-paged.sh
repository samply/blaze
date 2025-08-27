#!/bin/bash -e

#
# This script fetches the first page of Patient $everything, delete the
# Provenance resource of the patient and expects the size of the next page
# being still the same as without the deletion
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_IDENTIFIER="X79746011X"
PATIENT_ID=$(curl -s "$BASE/Patient?identifier=$PATIENT_IDENTIFIER" | jq -r '.entry[0].resource.id')
FIRST_PAGE=$(curl -s "$BASE/Patient/$PATIENT_ID/\$everything?_count=2000")
FIRST_PAGE_SIZE=$(echo "$FIRST_PAGE" | jq -r '.entry | length')
NEXT_LINK="$(echo "$FIRST_PAGE" | jq -r '.link[] | select(.relation == "next") | .url')"

# delete the Provenance resource of the patient
curl -sXDELETE "$BASE/Provenance?target=$PATIENT_ID" | jq

SECOND_PAGE="$(curl -sH "Accept: application/fhir+json" "$NEXT_LINK")"
SECOND_PAGE_SIZE=$(echo "$SECOND_PAGE" | jq -r '.entry | length')

test "first page size" "$FIRST_PAGE_SIZE" "2000"
test "second page size" "$SECOND_PAGE_SIZE" "1283"
