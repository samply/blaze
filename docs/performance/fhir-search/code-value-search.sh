#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
unit="kg"

count-resources() {
  local code="$1"
  local value="$2"
  local search_params="code=http://loinc.org|$code&value-quantity=lt$value|http://unitsofmeasure.org|$unit"

  echo "Counting Observations with code $code and value $value..."
  count-resources-raw "$base" "Observation" "$search_params"
}

download-resources() {
  local code="$1"
  local value="$2"
  local search_params="code=http://loinc.org|$code&value-quantity=lt$value|http://unitsofmeasure.org|$unit"

  echo "Downloading Observations with code $code and value $value..."
  download-resources-raw "$base" "Observation" "$search_params"
}

download-resources-subject() {
  local code="$1"
  local value="$2"
  local search_params="code=http://loinc.org|$code&value-quantity=lt$value|http://unitsofmeasure.org|$unit&_elements=subject"

  echo "Downloading Observations with code $code, value $value and _elements=subject..."
  download-resources-raw "$base" "Observation" "$search_params"
}

restart "$compose_file"
profile "$compose_file" "count-29463-7-26.8" count-resources "29463-7" "26.8"
profile "$compose_file" "download-29463-7-26.8" download-resources "29463-7" "26.8"
profile "$compose_file" "download-subject-29463-7-26.8" download-resources-subject "29463-7" "26.8"

restart "$compose_file"
profile "$compose_file" "count-29463-7-79.5" count-resources "29463-7" "79.5"
profile "$compose_file" "download-29463-7-79.5" download-resources "29463-7" "79.5"
profile "$compose_file" "download-subject-29463-7-79.5" download-resources-subject "29463-7" "79.5"

restart "$compose_file"
profile "$compose_file" "count-29463-7-183" count-resources "29463-7" "183"
profile "$compose_file" "download-29463-7-183" download-resources "29463-7" "183"
profile "$compose_file" "download-subject-29463-7-183" download-resources-subject "29463-7" "183"
