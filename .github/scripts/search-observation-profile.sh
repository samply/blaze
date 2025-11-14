#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
lab_count=$(curl -s "$base/Observation?_profile=http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab&_summary=count" | jq -r .total)

test "lab count" "$lab_count" "27218"
