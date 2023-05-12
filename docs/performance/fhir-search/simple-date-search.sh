#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
START_EPOCH="$(date +"%s")"

count-resources() {
  YEAR="$1"

  echo "Counting Observations with date in $YEAR..."
  count-resources-raw "$BASE" "date=$YEAR" "$START_EPOCH-count-$YEAR.times"
}

count-resources "2013"
count-resources "2019"
count-resources "2020"
