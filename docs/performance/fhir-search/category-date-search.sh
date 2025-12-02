#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
start_epoch="$(date +"%s")"

count-resources() {
  local category="$1"
  local year="$2"

  echo "Counting Observations with category $category and date $year..."
  count-resources-raw "$base" "Observation" "category=$category&date=$year" "$start_epoch-count-$category-$year.times"
}

restart "$compose_file"
count-resources "laboratory" "2013"
count-resources "laboratory" "2019"
count-resources "laboratory" "2020"

restart "$compose_file"
count-resources "vital-signs" "2013"
count-resources "vital-signs" "2019"
count-resources "vital-signs" "2020"
