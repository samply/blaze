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

create() {
  curl -sfH "Content-Type: application/fhir+json" -d  "{\"resourceType\": \"Patient\"}" "$base/Patient"
}

update() {
  curl -XPUT -sfH "Content-Type: application/fhir+json" -d @- -o /dev/null "$base/Patient/$1"
}

patient_id=$(create | jq -r '.id')

# update the patient to create a second version
patient "$patient_id" "male" | update "$patient_id"

first_page="$(curl -sH "Accept: application/fhir+json" "$base/Patient/$patient_id/_history?_count=1")"
total="$(echo "$first_page" | jq -r .total)"
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"

# update the patient to create a third version
patient "$patient_id" "female" | update "$patient_id"

second_page="$(curl -sH "Accept: application/fhir+json" "$next_link")"

test "first page total" "$total" "2"
test "second page total" "$(echo "$second_page" | jq -r .total)" "$total"

after_update_total="$(curl -sH "Accept: application/fhir+json" "$base/Patient/$patient_id/_history" | jq -r .total)"

test "after update total" "$after_update_total" "3"
