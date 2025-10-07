#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"
PATIENT_COUNT=1000
PATIENT_IDS="$(curl -sf "$BASE/Patient?birthdate=le1930&_count=$PATIENT_COUNT&_elements=id" | jq -r '.entry[].resource.id' | shuf | paste -sd ',' -)"

count-resources() {
  NAME="$1"
  TYPE="$2"
  CODES="$3"

  echo "Counting $NAME ${TYPE}s over $PATIENT_COUNT Patients..."
  count-resources-raw-post "$BASE" "$TYPE" "code=$CODES&patient=$PATIENT_IDS" "$START_EPOCH-count-$NAME.times"
}

download-resources() {
  NAME="$1"
  TYPE="$2"
  CODES="$3"

  echo "Downloading $NAME ${TYPE}s over $PATIENT_COUNT Patients..."
  download-resources-raw-post "$BASE" "$TYPE" "code=$CODES&patient=$PATIENT_IDS" "$START_EPOCH-download-$NAME.times"
}

restart "$COMPOSE_FILE"
NAME="10-observation-codes"
CODES="$(add_system "http://loinc.org" "$(cat "$SCRIPT_DIR/observation-codes-10.txt")")"
count-resources "$NAME" "Observation" "$CODES"
download-resources "$NAME" "Observation" "$CODES"

restart "$COMPOSE_FILE"
NAME="100-observation-codes"
CODES="$(add_system "http://loinc.org" "$(cat "$SCRIPT_DIR/observation-codes-100.txt")")"
count-resources "$NAME" "Observation" "$CODES"
download-resources "$NAME" "Observation" "$CODES"

restart "$COMPOSE_FILE"
NAME="1k-condition-codes"
CODES="$(add_system "http://snomed.info/sct" "$(cat "$SCRIPT_DIR/condition-codes-disease-1k.txt")")"
count-resources "$NAME" "Condition" "$CODES"
download-resources "$NAME" "Condition" "$CODES"

restart "$COMPOSE_FILE"
NAME="10k-condition-codes"
CODES="$(add_system "http://snomed.info/sct" "$(cat "$SCRIPT_DIR/condition-codes-disease-10k.txt")")"
count-resources "$NAME" "Condition" "$CODES"
download-resources "$NAME" "Condition" "$CODES"
