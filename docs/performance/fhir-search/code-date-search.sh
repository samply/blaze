#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  local code="$1"
  local year="$2"

  echo "Counting Observations with code $code and date $year..."
  count-resources-raw "$base" "Observation" "code=http://loinc.org|$code&date=$year"
}

download-resources() {
  local code="$1"
  local year="$2"

  echo "Downloading Observations with code $code and date $year..."
  download-resources-raw "$base" "Observation" "code=http://loinc.org|$code&date=$year"
}

restart "$compose_file"
profile "$compose_file" "count-8310-5-2013" count-resources "8310-5" "2013"
profile "$compose_file" "count-8310-5-2019" count-resources "8310-5" "2019"
profile "$compose_file" "count-8310-5-2020" count-resources "8310-5" "2020"
profile "$compose_file" "download-8310-5-2013" download-resources "8310-5" "2013"
profile "$compose_file" "download-8310-5-2019" download-resources "8310-5" "2019"
profile "$compose_file" "download-8310-5-2020" download-resources "8310-5" "2020"

restart "$compose_file"
profile "$compose_file" "count-55758-7-2013" count-resources "55758-7" "2013"
profile "$compose_file" "count-55758-7-2019" count-resources "55758-7" "2019"
profile "$compose_file" "count-55758-7-2020" count-resources "55758-7" "2020"
profile "$compose_file" "download-55758-7-2013" download-resources "55758-7" "2013"
profile "$compose_file" "download-55758-7-2019" download-resources "55758-7" "2019"
profile "$compose_file" "download-55758-7-2020" download-resources "55758-7" "2020"

restart "$compose_file"
profile "$compose_file" "count-72514-3-2013" count-resources "72514-3" "2013"
profile "$compose_file" "count-72514-3-2019" count-resources "72514-3" "2019"
profile "$compose_file" "count-72514-3-2020" count-resources "72514-3" "2020"
profile "$compose_file" "download-72514-3-2013" download-resources "72514-3" "2013"
profile "$compose_file" "download-72514-3-2019" download-resources "72514-3" "2019"
profile "$compose_file" "download-72514-3-2020" download-resources "72514-3" "2020"
