#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="$1"
patient_id=$(curl -sSfH 'Accept: application/fhir+json' "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')
first_page=$(fetch_instance_history "$base" Patient "$patient_id")
first_page_size=$(echo "$first_page" | jq -r '.entry | length')

test "first page size" "$first_page_size" "1"
