#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"

count-resources() {
  NAME="$1"
  CODES="$2"

  echo "Counting $NAME Observations..."
  count-resources-raw "$BASE" "Observation" "code=$CODES" "$START_EPOCH-count-$NAME.times"
}

download-resources() {
  NAME="$1"
  CODES="$2"

  echo "Downloading $NAME Observations..."
  download-resources-raw "$BASE" "Observation" "code=$CODES" "$START_EPOCH-download-$NAME.times"
}

#restart "$COMPOSE_FILE"
NAME="10-observation-codes"
CODES="$(add_system "http://loinc.org" "$(cat "$SCRIPT_DIR/observation-codes-10.txt")")"
#count-resources "$NAME" "$CODES"
#download-resources "$NAME" "$CODES"

restart "$COMPOSE_FILE"
NAME="100-observation-codes"
CODES="$(add_system "http://loinc.org" "$(cat "$SCRIPT_DIR/observation-codes-100.txt")")"
count-resources "$NAME" "$CODES"
download-resources "$NAME" "$CODES"
