#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
first_page=$(curl -s "$base/_history")
first_page_size=$(echo "$first_page" | jq -r '.entry | length')
next_link="$(echo "$first_page" | jq -r '.link[] | select(.relation == "next") | .url')"
second_page="$(curl -sH "Accept: application/fhir+json" "$next_link")"
second_page_size=$(echo "$second_page" | jq -r '.entry | length')

test "first page size" "$first_page_size" "50"
test "second page size" "$second_page_size" "50"
