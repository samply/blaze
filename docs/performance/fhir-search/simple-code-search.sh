#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
START_EPOCH="$(date +"%s")"

count-resources() {
  CODE="$1"

  echo "Counting Observations with code $CODE..."
  count-resources-raw "$BASE" "code=http://loinc.org|$CODE" "$START_EPOCH-count-$CODE.times"
}

download-resources() {
  CODE="$1"

  echo "Downloading Observations with code $CODE..."
  download-resources-raw "$BASE" "code=http://loinc.org|$CODE" "$START_EPOCH-download-$CODE.times"
}

download-resources-elements-subject() {
  CODE="$1"

  echo "Downloading Observations with code $CODE and _elements=subject..."
  download-resources-raw "$BASE" "code=http://loinc.org|$CODE&_elements=subject" "$START_EPOCH-download-subject-$CODE.times"
}

count-resources "17861-6"
count-resources "39156-5"
count-resources "29463-7"

download-resources "17861-6"
download-resources "39156-5"
download-resources "29463-7"

download-resources-elements-subject "17861-6"
download-resources-elements-subject "39156-5"
download-resources-elements-subject "29463-7"
