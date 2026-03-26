#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X79746011X"
patient_id=$(curl -sfH 'Accept: application/fhir+json' "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')
first_page=$(curl -sfH 'Accept: application/fhir+json' "$base/Patient/$patient_id/\$everything?_count=1000&start=2013&end=2014")
first_page_size=$(echo "$first_page" | jq -r '.entry | length')
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"
second_page="$(curl -sfH "Accept: application/fhir+json" "$next_link")"
second_page_size=$(echo "$second_page" | jq -r '.entry | length')

test "first page size" "$first_page_size" "1000"
test "second page size" "$second_page_size" "996"
