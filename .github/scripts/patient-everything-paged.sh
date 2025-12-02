#!/bin/bash -e

#
# This script fetches the first page of Patient $everything, delete the
# Provenance resource of the patient and expects the size of the next page
# being still the same as without the deletion
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X79746011X"
patient_id=$(curl -s "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')
first_page=$(curl -s "$base/Patient/$patient_id/\$everything?_count=2000")
first_page_size=$(echo "$first_page" | jq -r '.entry | length')
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"

# delete the Provenance resource of the patient
curl -sXDELETE "$base/Provenance?target=$patient_id" | jq

second_page="$(curl -sH "Accept: application/fhir+json" "$next_link")"
second_page_size=$(echo "$second_page" | jq -r '.entry | length')

test "first page size" "$first_page_size" "2000"
test "second page size" "$second_page_size" "1419"
