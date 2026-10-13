#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"

count-resources() {
  local date="$1"

  echo "Counting Patients with birthdate in $date..."
  count-resources-raw "$base" "Patient" "birthdate=$date"
}

download-resources() {
  local date="$1"

  echo "Downloading Patients with birthdate in $date..."
  download-resources-raw "$base" "Patient" "birthdate=$date"
}

download-resources-elements-subject() {
  local date="$1"

  echo "Downloading Patients with birthdate in $date and _elements=id..."
  download-resources-raw "$base" "Patient" "birthdate=$date&_elements=id"
}

restart "$compose_file"
profile "$compose_file" "count-gt1998-04-10" count-resources "gt1998-04-10"
profile "$compose_file" "download-gt1998-04-10" download-resources "gt1998-04-10"
profile "$compose_file" "download-subject-gt1998-04-10" download-resources-elements-subject "gt1998-04-10"

restart "$compose_file"
profile "$compose_file" "count-ge1998-04-10" count-resources "ge1998-04-10"
profile "$compose_file" "download-ge1998-04-10" download-resources "ge1998-04-10"
profile "$compose_file" "download-subject-ge1998-04-10" download-resources-elements-subject "ge1998-04-10"

restart "$compose_file"
profile "$compose_file" "count-lt1998-04-10" count-resources "lt1998-04-10"
profile "$compose_file" "download-lt1998-04-10" download-resources "lt1998-04-10"
profile "$compose_file" "download-subject-lt1998-04-10" download-resources-elements-subject "lt1998-04-10"

restart "$compose_file"
profile "$compose_file" "count-le1998-04-10" count-resources "le1998-04-10"
profile "$compose_file" "download-le1998-04-10" download-resources "le1998-04-10"
profile "$compose_file" "download-subject-le1998-04-10" download-resources-elements-subject "le1998-04-10"
