#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
PATIENT_IDENTIFIER="X81372825X"
PATIENT_ID=$(curl -s "$BASE/Patient?identifier=$PATIENT_IDENTIFIER" | jq -r '.entry[0].resource.id')
FIRST_PAGE=$(curl -s "$BASE/Patient/$PATIENT_ID/\$everything?_count=5000")
FIRST_PAGE_SIZE=$(echo "$FIRST_PAGE" | jq -r '.entry | length')
NEXT_LINK="$(echo "$FIRST_PAGE" | jq -r '.link[] | select(.relation == "next") | .url')"
SECOND_PAGE="$(curl -sH "Accept: application/fhir+json" "$NEXT_LINK")"
SECOND_PAGE_SIZE=$(echo "$SECOND_PAGE" | jq -r '.entry | length')
NEXT_LINK="$(echo "$SECOND_PAGE" | jq -r '.link[] | select(.relation == "next") | .url')"
THIRD_PAGE="$(curl -sH "Accept: application/fhir+json" "$NEXT_LINK")"
THIRD_PAGE_SIZE=$(echo "$THIRD_PAGE" | jq -r '.entry | length')

test "first page size (count 5000)" "$FIRST_PAGE_SIZE" "5000"
test "second page size (count 5000)" "$SECOND_PAGE_SIZE" "5000"
test "third page size (count 5000)" "$THIRD_PAGE_SIZE" "1900"

FIRST_PAGE=$(curl -s "$BASE/Patient/$PATIENT_ID/\$everything?_count=10000")
FIRST_PAGE_SIZE=$(echo "$FIRST_PAGE" | jq -r '.entry | length')
NEXT_LINK="$(echo "$FIRST_PAGE" | jq -r '.link[] | select(.relation == "next") | .url')"
SECOND_PAGE="$(curl -sH "Accept: application/fhir+json" "$NEXT_LINK")"
SECOND_PAGE_SIZE=$(echo "$SECOND_PAGE" | jq -r '.entry | length')

test "first page size (count 10000)" "$FIRST_PAGE_SIZE" "10000"
test "second page size (count 10000)" "$SECOND_PAGE_SIZE" "1900"
