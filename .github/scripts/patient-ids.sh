#!/bin/bash -e

# Returns the patient IDs if patient with identifiers taken as first argument.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifiers="${1//[[:space:]]/}"

# it's important to shuffle the IDs here in order to test that FHIR search works with unordered IDs
blazectl download --server "$base" Patient -p -q "identifier=$patient_identifiers" | jq -r '.id' | shuf | paste -sd, -
