#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X81372825X"
patient_id=$(curl -s "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')
first_page=$(curl -s "$base/Patient/$patient_id/\$everything?_count=5000")
first_page_size=$(echo "$first_page" | jq -r '.entry | length')
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"
second_page="$(curl -sH "Accept: application/fhir+json" "$next_link")"
second_page_size=$(echo "$second_page" | jq -r '.entry | length')
next_link="$(echo "$second_page" | jq -r '.link[] | select(.relation == "next") | .url')"
third_page="$(curl -sH "Accept: application/fhir+json" "$next_link")"
third_page_size=$(echo "$third_page" | jq -r '.entry | length')

test "first page size (count 5000)" "$first_page_size" "5000"
test "second page size (count 5000)" "$second_page_size" "5000"
test "third page size (count 5000)" "$third_page_size" "1904"

first_page=$(curl -s "$base/Patient/$patient_id/\$everything?_count=10000")
first_page_size=$(echo "$first_page" | jq -r '.entry | length')
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"
second_page="$(curl -sH "Accept: application/fhir+json" "$next_link")"
second_page_size=$(echo "$second_page" | jq -r '.entry | length')

test "first page size (count 10000)" "$first_page_size" "10000"
test "second page size (count 10000)" "$second_page_size" "1904"
