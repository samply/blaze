#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
start_epoch="$(date +"%s")"

count-resources() {
  local year="$1"

  echo "Counting Observations with date in $year..."
  count-resources-raw "$base" "Observation" "date=$year" "$start_epoch-count-$year.times"
}

download-resources() {
  local year="$1"

  echo "Downloading Observations with date in $year..."
  download-resources-raw "$base" "Observation" "date=$year" "$start_epoch-download-$year.times"
}

download-resources-elements-subject() {
  local year="$1"

  echo "Downloading Observations with date in $year and _elements=subject..."
  download-resources-raw "$base" "Observation" "date=$year&_elements=subject" "$start_epoch-download-subject-$year.times"
}

restart "$compose_file"
count-resources "2013"
download-resources "2013"
download-resources-elements-subject "2013"

restart "$compose_file"
count-resources "2019"
download-resources "2019"
download-resources-elements-subject "2019"
