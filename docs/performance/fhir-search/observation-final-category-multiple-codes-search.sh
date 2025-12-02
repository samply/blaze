#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
base="${2:-http://localhost:8080/fhir}"
start_epoch="$(date +"%s")"

count-resources() {
  local name="$1"
  local category="$2"
  local codes="$3"

  echo "Counting $name Observations..."
  count-resources-raw "$base" "Observation" "status=final&category=$category&code=$codes" "$start_epoch-count-$name.times"
}

download-resources() {
  local name="$1"
  local category="$2"
  local codes="$3"

  echo "Downloading $name Observations..."
  download-resources-raw "$base" "Observation" "status=final&category=$category&code=$codes" "$start_epoch-download-$name.times"
}

download-resources-elements-subject() {
  local name="$1"
  local category="$2"
  local codes="$3"

  echo "Downloading $name Observations with _elements=subject..."
  download-resources-raw "$base" "Observation" "status=final&category=$category&code=$codes&_elements=subject" "$start_epoch-download-$name.times"
}

restart "$compose_file"
name="top-5-laboratory-codes"
codes="http://loinc.org|49765-1,http://loinc.org|20565-8,http://loinc.org|2069-3,http://loinc.org|38483-4,http://loinc.org|2339-0"
count-resources "$name" "laboratory" "$codes"
download-resources "$name" "laboratory" "$codes"
download-resources-elements-subject "$name" "laboratory" "$codes"

restart "$compose_file"
name="low-5-vital-sign-codes"
codes="http://loinc.org|2713-6,http://loinc.org|8478-0,http://loinc.org|8310-5,http://loinc.org|77606-2,http://loinc.org|9843-4"
count-resources "$name" "vital-signs" "$codes"
download-resources "$name" "vital-signs" "$codes"
download-resources-elements-subject "$name" "vital-signs" "$codes"
