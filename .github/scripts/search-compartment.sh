#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_id=$(curl -s "$base/Patient?identifier=http://hl7.org/fhir/sid/us-ssn|999-82-5655" | jq -r '.entry[].resource.id')
observation_count=$(curl -s "$base/Patient/$patient_id/Observation?_summary=count" | jq .total)

test "observation count" "$observation_count" "1277"
