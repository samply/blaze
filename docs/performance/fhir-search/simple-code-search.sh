#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
start_epoch="$(date +"%s")"

count-resources() {
  local code="$1"

  echo "Counting Observations with code $code..."
  count-resources-raw "$base" "Observation" "code=http://loinc.org|$code" "$start_epoch-count-$code.times"
}

download-resources() {
  local code="$1"

  echo "Downloading Observations with code $code..."
  download-resources-raw "$base" "Observation" "code=http://loinc.org|$code" "$start_epoch-download-$code.times"
}

download-resources-elements-subject() {
  local code="$1"

  echo "Downloading Observations with code $code and _elements=subject..."
  download-resources-raw "$base" "Observation" "code=http://loinc.org|$code&_elements=subject" "$start_epoch-download-subject-$code.times"
}

restart "$compose_file"
count-resources "8310-5"
download-resources "8310-5"
download-resources-elements-subject "8310-5"

restart "$compose_file"
count-resources "55758-7"
download-resources "55758-7"
download-resources-elements-subject "55758-7"

restart "$compose_file"
count-resources "72514-3"
download-resources "72514-3"
download-resources-elements-subject "72514-3"
