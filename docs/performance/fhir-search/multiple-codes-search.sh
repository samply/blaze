#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
start_epoch="$(date +"%s")"

count-resources() {
  local name="$1"
  local codes="$2"

  echo "Counting $name Observations..."
  count-resources-raw "$base" "Observation" "code=$codes" "$start_epoch-count-$name.times"
}

download-resources() {
  local name="$1"
  local codes="$2"

  echo "Downloading $name Observations..."
  download-resources-raw "$base" "Observation" "code=$codes" "$start_epoch-download-$name.times"
}

restart "$compose_file"
name="10-observation-codes"
codes="$(add_system "http://loinc.org" "$(cat "$script_dir/observation-codes-10.txt")")"
count-resources "$name" "$codes"
download-resources "$name" "$codes"

restart "$compose_file"
name="100-observation-codes"
codes="$(add_system "http://loinc.org" "$(cat "$script_dir/observation-codes-100.txt")")"
count-resources "$name" "$codes"
download-resources "$name" "$codes"
