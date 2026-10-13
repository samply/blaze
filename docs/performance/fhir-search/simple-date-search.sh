#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  local year="$1"

  echo "Counting Observations with date in $year..."
  count-resources-raw "$base" "Observation" "date=$year"
}

download-resources() {
  local year="$1"

  echo "Downloading Observations with date in $year..."
  download-resources-raw "$base" "Observation" "date=$year"
}

download-resources-elements-subject() {
  local year="$1"

  echo "Downloading Observations with date in $year and _elements=subject..."
  download-resources-raw "$base" "Observation" "date=$year&_elements=subject"
}

restart "$compose_file"
profile "$compose_file" "count-2013" count-resources "2013"
profile "$compose_file" "download-2013" download-resources "2013"
profile "$compose_file" "download-subject-2013" download-resources-elements-subject "2013"

restart "$compose_file"
profile "$compose_file" "count-2019" count-resources "2019"
profile "$compose_file" "download-2019" download-resources "2019"
profile "$compose_file" "download-subject-2019" download-resources-elements-subject "2019"
