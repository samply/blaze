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

download-resources-elements-subject() {
  NAME="$1"
  CODES="$2"

  echo "Downloading $NAME Observations with _elements=subject..."
  download-resources-raw "$BASE" "Observation" "code=$CODES&_elements=subject" "$START_EPOCH-download-$NAME.times"
}

restart "$COMPOSE_FILE"
NAME="top-20-observation-codes"
CODES="http://loinc.org|72514-3,http://loinc.org|49765-1,http://loinc.org|20565-8,http://loinc.org|2069-3,http://loinc.org|38483-4,http://loinc.org|2339-0,http://loinc.org|6298-4,http://loinc.org|2947-0,http://loinc.org|6299-2,http://loinc.org|85354-9,http://loinc.org|29463-7,http://loinc.org|8867-4,http://loinc.org|9279-1,http://loinc.org|8302-2,http://loinc.org|72166-2,http://loinc.org|39156-5,http://loinc.org|93025-5,http://loinc.org|74006-8,http://loinc.org|55758-7,http://loinc.org|33914-3"
count-resources "$NAME" "$CODES"
download-resources "$NAME" "$CODES"
download-resources-elements-subject "$NAME" "$CODES"
