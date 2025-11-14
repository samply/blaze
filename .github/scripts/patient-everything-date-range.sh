#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X79746011X"
patient_id=$(curl -s "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')
bundle=$(curl -s "$base/Patient/$patient_id/\$everything?start=2013&end=2014")
actual_size=$(echo "$bundle" | jq -r .total)
ids="$(echo "$bundle" | jq -r '.entry[].resource.id')"

test "size" "$actual_size" "1997"

test "no duplicates" "$(echo "$ids" | sort -u | wc -l | xargs)" "$(echo "$ids" | wc -l | xargs)"

test "type counts" "$(echo "$bundle" | jq -r '.entry | group_by(.resource.resourceType)[] | [.[0].resource.resourceType, length] | @csv')" "$(cat "$script_dir/patient-everything/$patient_identifier-type-counts-2013-2014.csv")"
