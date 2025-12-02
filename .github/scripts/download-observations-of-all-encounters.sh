#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

encounter_id_list=$(blazectl --server "$base" download Encounter -q '_elements=id&_count=1000' 2>/dev/null | jq -r .id | tr '\n' ',')
num_observations=$(blazectl --server "$base" download Observation -p -q "encounter=$encounter_id_list&_count=1000" 2>/dev/null | wc -l | xargs)

test "number of Observations" "$num_observations" "42929"
