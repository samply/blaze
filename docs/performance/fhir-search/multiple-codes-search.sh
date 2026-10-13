#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  local name="$1"
  local codes="$2"

  echo "Counting $name Observations..."
  count-resources-raw "$base" "Observation" "code=$codes"
}

download-resources() {
  local name="$1"
  local codes="$2"

  echo "Downloading $name Observations..."
  download-resources-raw "$base" "Observation" "code=$codes"
}

restart "$compose_file"
name="10-observation-codes"
codes="$(add_system "http://loinc.org" "$(cat "$script_dir/observation-codes-10.txt")")"
profile "$compose_file" "count-$name" count-resources "$name" "$codes"
profile "$compose_file" "download-$name" download-resources "$name" "$codes"

restart "$compose_file"
name="100-observation-codes"
codes="$(add_system "http://loinc.org" "$(cat "$script_dir/observation-codes-100.txt")")"
profile "$compose_file" "count-$name" count-resources "$name" "$codes"
profile "$compose_file" "download-$name" download-resources "$name" "$codes"
