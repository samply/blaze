#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"

count-resources() {
  NAME="$1"
  CATEGORY="$2"
  CODES="$3"

  echo "Counting $NAME Observations..."
  count-resources-raw "$BASE" "Observation" "status=final&category=$CATEGORY&code=$CODES" "$START_EPOCH-count-$NAME.times"
}

download-resources() {
  NAME="$1"
  CATEGORY="$2"
  CODES="$3"

  echo "Downloading $NAME Observations..."
  download-resources-raw "$BASE" "Observation" "status=final&category=$CATEGORY&code=$CODES" "$START_EPOCH-download-$NAME.times"
}

download-resources-elements-subject() {
  NAME="$1"
  CATEGORY="$2"
  CODES="$3"

  echo "Downloading $NAME Observations with _elements=subject..."
  download-resources-raw "$BASE" "Observation" "status=final&category=$CATEGORY&code=$CODES&_elements=subject" "$START_EPOCH-download-$NAME.times"
}

restart "$COMPOSE_FILE"
NAME="top-5-laboratory-codes"
CODES="http://loinc.org|49765-1,http://loinc.org|20565-8,http://loinc.org|2069-3,http://loinc.org|38483-4,http://loinc.org|2339-0"
count-resources "$NAME" "laboratory" "$CODES"
download-resources "$NAME" "laboratory" "$CODES"
download-resources-elements-subject "$NAME" "laboratory" "$CODES"

restart "$COMPOSE_FILE"
NAME="low-5-vital-sign-codes"
CODES="http://loinc.org|2713-6,http://loinc.org|8478-0,http://loinc.org|8310-5,http://loinc.org|77606-2,http://loinc.org|9843-4"
count-resources "$NAME" "vital-signs" "$CODES"
download-resources "$NAME" "vital-signs" "$CODES"
download-resources-elements-subject "$NAME" "vital-signs" "$CODES"
