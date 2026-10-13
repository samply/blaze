#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  echo "Counting Procedures..."
  count-resources-raw "$base" "Procedure" "reason-reference:Condition.subject:Patient.birthdate=2020"
}

download-resources() {
  echo "Downloading Procedures..."
  download-resources-raw "$base" "Procedure" "reason-reference:Condition.subject:Patient.birthdate=2020"
}

restart "$compose_file"
profile "$compose_file" "count" count-resources
profile "$compose_file" "download" download-resources
