#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"
PATIENT_COUNT=1000
PATIENT_IDS="$(curl -sf "$BASE/Patient?birthdate=le1930&_count=$PATIENT_COUNT&_elements=id" | jq -r '.entry[].resource.id' | shuf | tr '\n' ',' | sed 's/,$//')"

count-resources() {
  CODE="$1"

  echo "Counting Observations with code $CODE and $PATIENT_COUNT Patients..."
  count-resources-raw-post "$BASE" "Observation" "code=http://loinc.org|$CODE&patient=$PATIENT_IDS" "$START_EPOCH-count-$CODE.times"
}

download-resources() {
  CODE="$1"

  echo "Downloading Observations with code $CODE and $PATIENT_COUNT Patients..."
  download-resources-raw-post "$BASE" "Observation" "code=http://loinc.org|$CODE&patient=$PATIENT_IDS" "$START_EPOCH-download-$CODE.times"
}

download-resources-elements-subject() {
  CODE="$1"

  echo "Downloading Observations with code $CODE, $PATIENT_COUNT Patients and _elements=subject..."
  download-resources-raw-post "$BASE" "Observation" "code=http://loinc.org|$CODE&patient=$PATIENT_IDS&_elements=subject" "$START_EPOCH-download-subject-$CODE.times"
}

restart "$COMPOSE_FILE"
count-resources "8310-5"
download-resources "8310-5"
download-resources-elements-subject "8310-5"

restart "$COMPOSE_FILE"
count-resources "55758-7"
download-resources "55758-7"
download-resources-elements-subject "55758-7"

restart "$COMPOSE_FILE"
count-resources "72514-3"
download-resources "72514-3"
download-resources-elements-subject "72514-3"
