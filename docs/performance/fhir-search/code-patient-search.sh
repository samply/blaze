#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"
PATIENT_COUNT=100
PATIENT_REFS="$(curl -sf "$BASE/Patient?_count=$PATIENT_COUNT&_elements=id" | jq -r '.entry[].resource.id | "Patient/" + .' | tr '\n' ',' | sed 's/,$//')"

count-resources() {
  CODE="$1"

  echo "Counting Observations with code $CODE and $PATIENT_COUNT Patients..."
  count-resources-raw "$BASE" "Observation" "code=http://loinc.org|$CODE&subject=Patient/$PATIENT_REFS" "$START_EPOCH-count-$CODE.times"
}

download-resources() {
  CODE="$1"

  echo "Downloading Observations with code $CODE and $PATIENT_COUNT Patients..."
  download-resources-raw "$BASE" "Observation" "code=http://loinc.org|$CODE&subject=Patient/$PATIENT_REFS" "$START_EPOCH-download-$CODE.times"
}

download-resources-elements-subject() {
  CODE="$1"

  echo "Downloading Observations with code $CODE, $PATIENT_COUNT Patients and _elements=subject..."
  download-resources-raw "$BASE" "Observation" "code=http://loinc.org|$CODE&subject=Patient/$PATIENT_REFS&_elements=subject" "$START_EPOCH-download-subject-$CODE.times"
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
