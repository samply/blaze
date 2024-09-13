#!/bin/bash -e

#
# This script does the following:
#
#  * creates a patient
#  * updates that patient to create a second version
#  * expects the history to contain two entries
#  * deletes the history of the patient
#  * expects the history to contain only the current entry
#  * expects a versioned read of the deleted history entry to return 404
#  * expects the type history doesn't contain the deleted history entry
#  * expects the system history doesn't contain the deleted history entry
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
  echo "▶️update patient"
  curl -XPUT -sfH "Content-Type: application/fhir+json" -d @- -o /dev/null "$BASE/Patient/$1"
}

get_instance_history() {
  curl -sH "Accept: application/fhir+json" "$BASE/Patient/$1/_history"
}

get_type_history() {
  curl -sH "Accept: application/fhir+json" "$BASE/Patient/_history"
}

get_system_history() {
  curl -sH "Accept: application/fhir+json" "$BASE/_history"
}

delete_history() {
  echo "▶️delete history"
  curl -XDELETE -sf -o /dev/null "$BASE/Patient/$1/_history"
}

vread() {
  curl -sH "Accept: application/fhir+json" -w '%{response_code}' -o /dev/null "$BASE/Patient/$1/_history/$2"
}

echo "▶️create patient"
PATIENT_ID=$(create | jq -r '.id')

# update the patient to create a second version
patient "$PATIENT_ID" "male" | update "$PATIENT_ID"

# expect the history to contain two entries
HISTORY="$(get_instance_history "$PATIENT_ID")"
test "instance history total" "$(echo "$HISTORY" | jq -r .total)" "2"
ENTRY_1_VID="$(echo "$HISTORY" | jq -r '.entry[0].resource.meta.versionId')"
ENTRY_2_VID="$(echo "$HISTORY" | jq -r '.entry[1].resource.meta.versionId')"

# delete the history of the patient
delete_history "$PATIENT_ID"

# expect the history to contain only the current entry
HISTORY="$(get_instance_history "$PATIENT_ID")"
test "instance history total" "$(echo "$HISTORY" | jq -r .total)" "1"
test "instance history entry versionId" "$(echo "$HISTORY" | jq -r '.entry[0].resource.meta.versionId')" "$ENTRY_1_VID"
test "instance history entry gender" "$(echo "$HISTORY" | jq -r '.entry[0].resource.gender')" "male"

# expect a versioned read of the deleted history entry to return 404
test "read deleted history entry status code" "$(vread "$PATIENT_ID" "$ENTRY_2_VID")" "404"

# expect the type history doesn't contain the deleted history entry
HISTORY="$(get_type_history)"
test "type history first entry resource id" "$(echo "$HISTORY" | jq -r '.entry[0].resource.id')" "$PATIENT_ID"
test "type history first entry resource versionId" "$(echo "$HISTORY" | jq -r '.entry[0].resource.meta.versionId')" "$ENTRY_1_VID"
test_not_equal "type history second entry resource id" "$(echo "$HISTORY" | jq -r '.entry[1].resource.id')" "$PATIENT_ID"

# expect the system history doesn't contain the deleted history entry
HISTORY="$(get_system_history)"
test "system history first entry resource id" "$(echo "$HISTORY" | jq -r '.entry[0].resource.id')" "$PATIENT_ID"
test "system history first entry resource versionId" "$(echo "$HISTORY" | jq -r '.entry[0].resource.meta.versionId')" "$ENTRY_1_VID"
test_not_equal "system history second entry resource id" "$(echo "$HISTORY" | jq -r '.entry[1].resource.id')" "$PATIENT_ID"
