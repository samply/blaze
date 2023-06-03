#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="${1:-http://localhost:8080/fhir}"
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

count-resources "8310-5"
download-resources "8310-5"
download-resources-elements-subject "8310-5"

count-resources "55758-7"
download-resources "55758-7"
download-resources-elements-subject "55758-7"

count-resources "72514-3"
download-resources "72514-3"
download-resources-elements-subject "72514-3"
