#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
UNIT="kg"
START_EPOCH="$(date +"%s")"

count-resources() {
  CODE="$1"
  VALUE="$2"
  SEARCH_PARAMS="code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT"

  echo "Counting Observations with code $CODE and value $VALUE..."
  count-resources-raw "$BASE" "Observation" "$SEARCH_PARAMS" "$START_EPOCH-count-$CODE-value-$VALUE.times"
}

download-resources() {
  CODE="$1"
  VALUE="$2"
  SEARCH_PARAMS="code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT"

  echo "Downloading Observations with code $CODE and value $VALUE..."
  download-resources-raw "$BASE" "Observation" "$SEARCH_PARAMS" "$START_EPOCH-download-$CODE-value-$VALUE.times"
}

download-resources-subject() {
  CODE="$1"
  VALUE="$2"
  SEARCH_PARAMS="code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_elements=subject"

  echo "Downloading Observations with code $CODE, value $VALUE and _elements=subject..."
  download-resources-raw "$BASE" "Observation" "$SEARCH_PARAMS" "$START_EPOCH-download-$CODE-value-$VALUE-subject.times"
}

restart "$COMPOSE_FILE"
count-resources "29463-7" "26.8"
download-resources "29463-7" "26.8"
download-resources-subject "29463-7" "26.8"

restart "$COMPOSE_FILE"
count-resources "29463-7" "79.5"
download-resources "29463-7" "79.5"
download-resources-subject "29463-7" "79.5"

restart "$COMPOSE_FILE"
count-resources "29463-7" "183"
download-resources "29463-7" "183"
download-resources-subject "29463-7" "183"
