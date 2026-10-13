#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
patient_count=1000
patient_ids="$(curl -sf "$base/Patient?birthdate=le1930&_count=$patient_count&_elements=id" | jq -r '.entry[].resource.id' | shuf | tr '\n' ',' | sed 's/,$//')"

count-resources() {
  local code="$1"

  echo "Counting Observations with code $code and $patient_count Patients..."
  count-resources-raw-post "$base" "Observation" "code=http://loinc.org|$code&patient=$patient_ids"
}

download-resources() {
  local code="$1"

  echo "Downloading Observations with code $code and $patient_count Patients..."
  download-resources-raw-post "$base" "Observation" "code=http://loinc.org|$code&patient=$patient_ids"
}

download-resources-elements-subject() {
  local code="$1"

  echo "Downloading Observations with code $code, $patient_count Patients and _elements=subject..."
  download-resources-raw-post "$base" "Observation" "code=http://loinc.org|$code&patient=$patient_ids&_elements=subject"
}

restart "$compose_file"
profile "$compose_file" "count-8310-5" count-resources "8310-5"
profile "$compose_file" "download-8310-5" download-resources "8310-5"
profile "$compose_file" "download-subject-8310-5" download-resources-elements-subject "8310-5"

restart "$compose_file"
profile "$compose_file" "count-55758-7" count-resources "55758-7"
profile "$compose_file" "download-55758-7" download-resources "55758-7"
profile "$compose_file" "download-subject-55758-7" download-resources-elements-subject "55758-7"

restart "$compose_file"
profile "$compose_file" "count-72514-3" count-resources "72514-3"
profile "$compose_file" "download-72514-3" download-resources "72514-3"
profile "$compose_file" "download-subject-72514-3" download-resources-elements-subject "72514-3"
