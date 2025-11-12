#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
start_epoch="$(date +"%s")"

count-resources() {
  local date="$1"

  echo "Counting Patients with birthdate in $date..."
  count-resources-raw "$base" "Patient" "birthdate=$date" "$start_epoch-count-$date.times"
}

download-resources() {
  local date="$1"

  echo "Downloading Patients with birthdate in $date..."
  download-resources-raw "$base" "Patient" "birthdate=$date" "$start_epoch-download-$date.times"
}

download-resources-elements-subject() {
  local date="$1"

  echo "Downloading Patients with birthdate in $date and _elements=id..."
  download-resources-raw "$base" "Patient" "birthdate=$date&_elements=id" "$start_epoch-download-subject-$date.times"
}

restart "$compose_file"
count-resources "gt1998-04-10"
download-resources "gt1998-04-10"
download-resources-elements-subject "gt1998-04-10"

restart "$compose_file"
count-resources "ge1998-04-10"
download-resources "ge1998-04-10"
download-resources-elements-subject "ge1998-04-10"

restart "$compose_file"
count-resources "lt1998-04-10"
download-resources "lt1998-04-10"
download-resources-elements-subject "lt1998-04-10"

restart "$compose_file"
count-resources "le1998-04-10"
download-resources "le1998-04-10"
download-resources-elements-subject "le1998-04-10"
