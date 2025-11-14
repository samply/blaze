#!/bin/bash -e

#
# This script fetches the first system history bundle, adds a new Patient and expects
# the total on the next page being still the same
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

first_page="$(curl -sH "Accept: application/fhir+json" "$base/_history")"
total="$(echo "$first_page" | jq -r .total)"
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"

# create new patient
curl -sfH 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "{\"resourceType\": \"Patient\"}" -o /dev/null "$base/Patient"

second_page="$(curl -sH "Accept: application/fhir+json" "$next_link")"

test "second page total" "$(echo "$second_page" | jq -r .total)" "$total"
