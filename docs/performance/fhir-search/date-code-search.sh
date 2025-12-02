#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
start_epoch="$(date +"%s")"

count-resources() {
  local year="$1"

  echo "Counting Laboratory Observations with date in $year..."
  count-resources-raw "$base" "Observation" "date=$year&category=laboratory" "$start_epoch-count-$year-laboratory.times"
}

restart "$compose_file"
count-resources "2013"

restart "$compose_file"
count-resources "2019"

restart "$compose_file"
count-resources "2020"
