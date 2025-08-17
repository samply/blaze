#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"

count-resources() {
  CATEGORY="$1"
  YEAR="$2"

  echo "Counting Observations with category $CATEGORY and date $YEAR..."
  count-resources-raw "$BASE" "Observation" "category=$CATEGORY&date=$YEAR" "$START_EPOCH-count-$CATEGORY-$YEAR.times"
}

restart "$COMPOSE_FILE"
count-resources "laboratory" "2013"
count-resources "laboratory" "2019"
count-resources "laboratory" "2020"

restart "$COMPOSE_FILE"
count-resources "vital-signs" "2013"
count-resources "vital-signs" "2019"
count-resources "vital-signs" "2020"
