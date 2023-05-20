#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="${1:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"

count-resources() {
  YEAR="$1"

  echo "Counting Observations with date in $YEAR..."
  count-resources-raw "$BASE" "date=$YEAR" "$START_EPOCH-count-$YEAR.times"
}

download-resources() {
  YEAR="$1"

  echo "Downloading Observations with date in $YEAR..."
  download-resources-raw "$BASE" "date=$YEAR" "$START_EPOCH-download-$YEAR.times"
}

download-resources-elements-subject() {
  YEAR="$1"

  echo "Downloading Observations with date in $YEAR and _elements=subject..."
  download-resources-raw "$BASE" "date=$YEAR&_elements=subject" "$START_EPOCH-download-subject-$YEAR.times"
}

count-resources "2013"
download-resources "2013"
download-resources-elements-subject "2013"

count-resources "2019"
download-resources "2019"
download-resources-elements-subject "2019"
