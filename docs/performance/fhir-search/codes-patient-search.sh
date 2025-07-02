#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"
PATIENT_COUNT=1000
PATIENT_IDS="$(curl -sf "$BASE/Patient?birthdate=le1930&_count=$PATIENT_COUNT&_elements=id" | jq -r '.entry[].resource.id' | shuf | tr '\n' ',' | sed 's/,$//')"

count-resources() {
  NAME="$1"
  CODES="$2"

  echo "Counting $NAME Conditions over $PATIENT_COUNT Patients..."
  count-resources-raw-post "$BASE" "Condition" "code=$CODES&patient=$PATIENT_IDS" "$START_EPOCH-count-$NAME.times"
}

download-resources() {
  NAME="$1"
  CODES="$2"

  echo "Downloading $NAME Conditions over $PATIENT_COUNT Patients..."
  download-resources-raw-post "$BASE" "Condition" "code=$CODES&patient=$PATIENT_IDS" "$START_EPOCH-download-$NAME.times"
}

restart "$COMPOSE_FILE"
NAME="common-disorders"
CODES="http://snomed.info/sct|444814009,http://snomed.info/sct|195662009,http://snomed.info/sct|10509002,http://snomed.info/sct|271737000,http://snomed.info/sct|40055000,http://snomed.info/sct|233604007,http://snomed.info/sct|389087006,http://snomed.info/sct|75498004"
count-resources "$NAME" "$CODES"
download-resources "$NAME" "$CODES"
