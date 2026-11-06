#!/bin/bash
set -euo pipefail

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

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "id": "$1",
  "gender": "$2"
}
END
}

delete_history() {
  echo "▶️delete history"
  curl -XDELETE -sSf -o /dev/null "$base/Patient/$1/_history"
}

vread() {
  curl -sH "Accept: application/fhir+json" -w '%{response_code}' -o /dev/null "$base/Patient/$1/_history/$2"
}

echo "▶️create patient"
patient_id=$(echo '{"resourceType": "Patient"}' | create "$base/Patient" | jq -r '.id')

# update the patient to create a second version
echo "▶️update patient"
patient "$patient_id" "male" | update "$base/Patient/$patient_id" > /dev/null

# expect the history to contain two entries
history="$(fetch_instance_history "$base" Patient "$patient_id")"
test "instance history total" "$(echo "$history" | jq -r .total)" "2"
entry_1_vid="$(echo "$history" | jq -r '.entry[0].resource.meta.versionId')"
entry_2_vid="$(echo "$history" | jq -r '.entry[1].resource.meta.versionId')"

# delete the history of the patient
delete_history "$patient_id"

# expect the history to contain only the current entry
history="$(fetch_instance_history "$base" Patient "$patient_id")"
test "instance history total" "$(echo "$history" | jq -r .total)" "1"
test "instance history entry versionId" "$(echo "$history" | jq -r '.entry[0].resource.meta.versionId')" "$entry_1_vid"
test "instance history entry gender" "$(echo "$history" | jq -r '.entry[0].resource.gender')" "male"

# expect a versioned read of the deleted history entry to return 404
test "read deleted history entry status code" "$(vread "$patient_id" "$entry_2_vid")" "404"

# expect the type history doesn't contain the deleted history entry
history="$(fetch_type_history "$base" Patient)"
test "type history first entry resource id" "$(echo "$history" | jq -r '.entry[0].resource.id')" "$patient_id"
test "type history first entry resource versionId" "$(echo "$history" | jq -r '.entry[0].resource.meta.versionId')" "$entry_1_vid"
test_not_equal "type history second entry resource id" "$(echo "$history" | jq -r '.entry[1].resource.id')" "$patient_id"

# expect the system history doesn't contain the deleted history entry
history="$(fetch_system_history "$base")"
test "system history first entry resource id" "$(echo "$history" | jq -r '.entry[0].resource.id')" "$patient_id"
test "system history first entry resource versionId" "$(echo "$history" | jq -r '.entry[0].resource.meta.versionId')" "$entry_1_vid"
test_not_equal "system history second entry resource id" "$(echo "$history" | jq -r '.entry[1].resource.id')" "$patient_id"
