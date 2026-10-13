#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
patient_count=1000
patient_ids="$(curl -sf "$base/Patient?birthdate=le1930&_count=$patient_count&_elements=id" | jq -r '.entry[].resource.id' | shuf | paste -sd ',' -)"

count-resources() {
  local name="$1"
  local type="$2"
  local codes="$3"

  echo "Counting $name ${type}s over $patient_count Patients..."
  count-resources-raw-post "$base" "$type" "code=$codes&patient=$patient_ids"
}

download-resources() {
  local name="$1"
  local type="$2"
  local codes="$3"

  echo "Downloading $name ${type}s over $patient_count Patients..."
  download-resources-raw-post "$base" "$type" "code=$codes&patient=$patient_ids"
}

restart "$compose_file"
name="10-observation-codes"
codes="$(add_system "http://loinc.org" "$(cat "$script_dir/observation-codes-10.txt")")"
profile "$compose_file" "count-$name" count-resources "$name" "Observation" "$codes"
profile "$compose_file" "download-$name" download-resources "$name" "Observation" "$codes"

restart "$compose_file"
name="100-observation-codes"
codes="$(add_system "http://loinc.org" "$(cat "$script_dir/observation-codes-100.txt")")"
profile "$compose_file" "count-$name" count-resources "$name" "Observation" "$codes"
profile "$compose_file" "download-$name" download-resources "$name" "Observation" "$codes"

restart "$compose_file"
name="1k-condition-codes"
codes="$(add_system "http://snomed.info/sct" "$(cat "$script_dir/condition-codes-disease-1k.txt")")"
profile "$compose_file" "count-$name" count-resources "$name" "Condition" "$codes"
profile "$compose_file" "download-$name" download-resources "$name" "Condition" "$codes"

restart "$compose_file"
name="10k-condition-codes"
codes="$(add_system "http://snomed.info/sct" "$(cat "$script_dir/condition-codes-disease-10k.txt")")"
profile "$compose_file" "count-$name" count-resources "$name" "Condition" "$codes"
profile "$compose_file" "download-$name" download-resources "$name" "Condition" "$codes"
