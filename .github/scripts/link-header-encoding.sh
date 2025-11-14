#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

url="$base/Patient?name=Le%C3%B3n"
total=$(curl -s -H 'Accept: application/fhir+json' "$url&_summary=count" | jq .total)
headers=$(curl -s -H 'Accept: application/fhir+json' -o /dev/null -D - "$url")
link_header=$(echo "$headers" | grep -i link | tr -d '\r')

test "number of patients found" "$total" "1"
test "encoded search param value" "$(echo "$link_header" | awk -F'[;,<>?&=]' '{print $9}')" "Le%C3%B3n"

url="$base/Condition?code=59621000,10509002"
total=$(curl -s -H 'Accept: application/fhir+json' "$url&_summary=count" | jq .total)
headers=$(curl -s -H 'Accept: application/fhir+json' -o /dev/null -D - "$url")
link_header=$(echo "$headers" | grep -i link | tr -d '\r')

test "number of conditions found" "$total" "93"
test "encoded search param value" "$(echo "$link_header" | awk -F'[;,<>?&=]' '{print $9}')" "59621000%2C10509002"
