#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
headers=$(curl -s -H 'Content-Type: application/fhir+json' -H 'Accept: application/fhir+json' -d "{\"resourceType\": \"Patient\"}" -o /dev/null -D - "$base/Patient")
location_header=$(echo "$headers" | grep -i location | tr -d '\r')

test "Location header prefix" "$(echo "\"${location_header:10}\"" | jq -r '[split("/") | limit(5;.[])] | join("/")')" "$base/Patient"
