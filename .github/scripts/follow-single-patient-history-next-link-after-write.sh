#!/bin/bash
set -euo pipefail

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

patient_id=$(echo '{"resourceType": "Patient"}' | create "$base/Patient" | jq -r '.id')

# update the patient to create a second version
patient "$patient_id" "male" | update "$base/Patient/$patient_id" > /dev/null

first_page="$(fetch_instance_history "$base" Patient "$patient_id" "_count=1")"
total="$(echo "$first_page" | jq -r .total)"
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"

# update the patient to create a third version
patient "$patient_id" "female" | update "$base/Patient/$patient_id" > /dev/null

second_page="$(curl -sSfH "Accept: application/fhir+json" "$next_link")"

test "first page total" "$total" "2"
test "second page total" "$(echo "$second_page" | jq -r .total)" "$total"

after_update_total="$(fetch_instance_history "$base" Patient "$patient_id" | jq -r .total)"

test "after update total" "$after_update_total" "3"
