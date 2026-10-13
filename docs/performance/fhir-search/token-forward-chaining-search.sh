#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  echo "Counting Observations..."
  count-resources-raw "$base" "Observation" "category=laboratory&patient.birthdate=2020-01,2020-02"
}

download-resources() {
  echo "Downloading Observations..."
  download-resources-raw "$base" "Observation" "category=laboratory&patient.birthdate=2020-01,2020-02"
}

restart "$compose_file"
profile "$compose_file" "count" count-resources
profile "$compose_file" "download" download-resources
