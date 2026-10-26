#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  local year="$1"

  echo "Counting Laboratory Observations with date in $year..."
  count-resources-raw "$base" "Observation" "date=$year&category=laboratory"
}

restart "$compose_file"
profile "$compose_file" "count-2013" count-resources "2013"

restart "$compose_file"
profile "$compose_file" "count-2019" count-resources "2019"

restart "$compose_file"
profile "$compose_file" "count-2020" count-resources "2020"
