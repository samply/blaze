#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  local category="$1"
  local year="$2"

  echo "Counting Observations with category $category and date $year..."
  count-resources-raw "$base" "Observation" "category=$category&date=$year"
}

restart "$compose_file"
profile "$compose_file" "count-laboratory-2013" count-resources "laboratory" "2013"
profile "$compose_file" "count-laboratory-2019" count-resources "laboratory" "2019"
profile "$compose_file" "count-laboratory-2020" count-resources "laboratory" "2020"

restart "$compose_file"
profile "$compose_file" "count-vital-signs-2013" count-resources "vital-signs" "2013"
profile "$compose_file" "count-vital-signs-2019" count-resources "vital-signs" "2019"
profile "$compose_file" "count-vital-signs-2020" count-resources "vital-signs" "2020"
