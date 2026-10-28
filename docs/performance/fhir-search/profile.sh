#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  local year="$1"

  echo "Counting Observations of Patients born in $year..."
  count-resources-raw "$base" "Observation" "subject:Patient.birthdate=$year"
}

restart "$compose_file"
profile "$compose_file" "chained-birthdate-2019" count-resources "2019"

restart "$compose_file"
profile "$compose_file" "chained-birthdate-2020" count-resources "2020"
