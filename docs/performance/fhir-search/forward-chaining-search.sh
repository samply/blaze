#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
start_epoch="$(date +"%s")"

count-resources() {
  echo "Counting Procedures..."
  count-resources-raw "$base" "Procedure" "reason-reference:Condition.subject:Patient.birthdate=2020" "$start_epoch-count.times"
}

download-resources() {
  echo "Downloading Procedures..."
  download-resources-raw "$base" "Procedure" "reason-reference:Condition.subject:Patient.birthdate=2020" "$start_epoch-download.times"
}

restart "$compose_file"
count-resources
download-resources
