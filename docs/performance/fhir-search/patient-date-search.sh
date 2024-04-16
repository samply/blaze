#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"
BASE="${2:-http://localhost:8080/fhir}"
START_EPOCH="$(date +"%s")"

count-resources() {
  DATE="$1"

  echo "Counting Patients with birthdate in $DATE..."
  count-resources-raw "$BASE" "Patient" "birthdate=$DATE" "$START_EPOCH-count-$DATE.times"
}

download-resources() {
  DATE="$1"

  echo "Downloading Patients with birthdate in $DATE..."
  download-resources-raw "$BASE" "Patient" "birthdate=$DATE" "$START_EPOCH-download-$DATE.times"
}

download-resources-elements-subject() {
  DATE="$1"

  echo "Downloading Patients with birthdate in $DATE and _elements=id..."
  download-resources-raw "$BASE" "Patient" "birthdate=$DATE&_elements=id" "$START_EPOCH-download-subject-$DATE.times"
}

restart "$COMPOSE_FILE"
count-resources "gt1998-04-10"
download-resources "gt1998-04-10"
download-resources-elements-subject "gt1998-04-10"

restart "$COMPOSE_FILE"
count-resources "ge1998-04-10"
download-resources "ge1998-04-10"
download-resources-elements-subject "ge1998-04-10"

restart "$COMPOSE_FILE"
count-resources "lt1998-04-10"
download-resources "lt1998-04-10"
download-resources-elements-subject "lt1998-04-10"

restart "$COMPOSE_FILE"
count-resources "le1998-04-10"
download-resources "le1998-04-10"
download-resources-elements-subject "le1998-04-10"
