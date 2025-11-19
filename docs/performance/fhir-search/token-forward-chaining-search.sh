#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
start_epoch="$(date +"%s")"

count-resources() {
  echo "Counting Observations..."
  count-resources-raw "$base" "Observation" "category=laboratory&patient.birthdate=2020-01,2020-02" "$start_epoch-count.times"
}

download-resources() {
  echo "Downloading Observations..."
  download-resources-raw "$base" "Observation" "category=laboratory&patient.birthdate=2020-01,2020-02" "$start_epoch-download.times"
}

restart "$compose_file"
count-resources
download-resources
