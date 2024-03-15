#!/bin/bash -e

#
# This script does the following:
#
#  * creates a patient
#  * updates that patient in order to create a history with two entries
#  * fetches the first page of that patient history bundle
#  * updates that patient again
#  * expects the total of the second page to be the same as of that of the first page
#  * fetches a new history bundle again and expects the total to be increased
#

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "id": "$1",
  "gender": "$2"
}
END
}

create() {
  curl -sfH "Content-Type: application/fhir+json" -d  "{\"resourceType\": \"Patient\"}" "$BASE/Patient"
}

update() {
  curl -XPUT -sfH "Content-Type: application/fhir+json" -d @- -o /dev/null "$BASE/Patient/$1"
}

PATIENT_ID=$(create | jq -r '.id')

# update the patient to create a second version
patient "$PATIENT_ID" "male" | update "$PATIENT_ID"

FIRST_PAGE="$(curl -sH "Accept: application/fhir+json" "$BASE/Patient/$PATIENT_ID/_history?_count=1")"
TOTAL="$(echo "$FIRST_PAGE" | jq -r .total)"
NEXT_LINK="$(echo "$FIRST_PAGE" | jq -r '.link[] | select(.relation == "next") | .url')"

# update the patient to create a third version
patient "$PATIENT_ID" "female" | update "$PATIENT_ID"

SECOND_PAGE="$(curl -sH "Accept: application/fhir+json" "$NEXT_LINK")"

test "first page total" "$TOTAL" "2"
test "second page total" "$(echo "$SECOND_PAGE" | jq -r .total)" "$TOTAL"

AFTER_UPDATE_TOTAL="$(curl -sH "Accept: application/fhir+json" "$BASE/Patient/$PATIENT_ID/_history" | jq -r .total)"

test "after update total" "$AFTER_UPDATE_TOTAL" "3"
