#!/bin/bash -e
set -o pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X81372825X"
patient_id=$(curl -sfH 'Accept: application/fhir+json' "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')
error_code=$(curl -sH 'Accept: application/fhir+json' "$base/Patient/$patient_id/\$everything" |  jq -r '.issue[0].code')

test "error code" "$error_code" "too-costly"
