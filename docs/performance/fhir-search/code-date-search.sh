#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"

count-resources() {
  YEAR="$1"

  echo "Counting Laboratory Observations with date in $YEAR..."
  count-resources-raw "$BASE" "category=laboratory&date=$YEAR" "$START_EPOCH-count-laboratory-$YEAR.times"
}

restart "$COMPOSE_FILE"
count-resources "2013"

restart "$COMPOSE_FILE"
count-resources "2019"

restart "$COMPOSE_FILE"
count-resources "2020"
