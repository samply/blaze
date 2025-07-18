#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"

count-resources() {
  CODE="$1"
  YEAR="$2"

  echo "Counting Observations with code $CODE and date $YEAR..."
  count-resources-raw "$BASE" "Observation" "code=http://loinc.org|$CODE&date=$YEAR" "$START_EPOCH-count-$CODE-$YEAR.times"
}

download-resources() {
  CODE="$1"
  YEAR="$2"

  echo "Downloading Observations with code $CODE and date $YEAR..."
  download-resources-raw "$BASE" "Observation" "code=http://loinc.org|$CODE&date=$YEAR" "$START_EPOCH-download-$CODE-$YEAR.times"
}

restart "$COMPOSE_FILE"
count-resources "8310-5" "2013"
download-resources "8310-5" "2013"

restart "$COMPOSE_FILE"
count-resources "8310-5" "2019"
download-resources "8310-5" "2019"

restart "$COMPOSE_FILE"
count-resources "8310-5" "2020"
download-resources "8310-5" "2020"
