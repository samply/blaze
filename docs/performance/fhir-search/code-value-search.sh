#!/bin/bash -e

BASE="http://localhost:8080/fhir"
UNIT="mg/dL"

count-resources() {
  CODE="$1"
  VALUE="$2"
  PERCENT="$3"
  SEARCH_PARAMS="code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT"
  TIMES_FILE="count-$CODE-value-$PERCENT-percent.times"

  echo "Counting Observations with code $CODE and $PERCENT percent hits..."
  for i in {1..6}; do
    /usr/bin/time -f "%e" -a -o "$TIMES_FILE" curl -s "$BASE/Observation?$SEARCH_PARAMS&_summary=count" | jq .total
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "$TIMES_FILE" | awk '{t+=$1;n++} END {printf("Avg time: %.1f\n", t/n)}'
}

count-resources "17861-6" "8.67" "10"
count-resources "17861-6" "9.35" "50"
count-resources "17861-6" "10.2" "100"

download-resources-raw() {
  SEARCH_PARAMS="$1"
  TIMES_FILE="$2"

  for i in {1..6}; do
    /usr/bin/time -f "%e" -a -o "$TIMES_FILE" blazectl download --server "$BASE" Observation -q "$SEARCH_PARAMS&_count=1000" >/dev/null 2>/dev/null
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "$TIMES_FILE" | awk '{t+=$1;n++} END {printf("Avg time: %.1f\n", t/n)}'
}

download-resources() {
  CODE="$1"
  VALUE="$2"
  PERCENT="$3"

  echo "Downloading Observations with code $CODE and $PERCENT percent hits..."
  download-resources-raw "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT" "download-$CODE-value-$PERCENT-percent.times"
}

download-resources "17861-6" "8.67" "10"
download-resources "17861-6" "9.35" "50"
download-resources "17861-6" "10.2" "100"

download-resources-subject() {
  CODE="$1"
  VALUE="$2"
  PERCENT="$3"

  echo "Downloading Observations with code $CODE, $PERCENT percent hits and _elements=subject..."
  download-resources-raw "code=http://loinc.org|$CODE&value-quantity=lt$VALUE|http://unitsofmeasure.org|$UNIT&_elements=subject" "download-$CODE-value-$PERCENT-percent-subject.times"
}

download-resources-subject "17861-6" "8.67" "10"
download-resources-subject "17861-6" "9.35" "50"
download-resources-subject "17861-6" "10.2" "100"

download-resources-combined() {
  CODE="$1"
  VALUE="$2"
  PERCENT="$3"

  echo "Downloading Observations with code $CODE and $PERCENT percent hits and combined search param..."
  download-resources-raw "code-value-quantity=http://loinc.org|$CODE\$lt$VALUE|http://unitsofmeasure.org|$UNIT" "download-$CODE-value-$PERCENT-percent-combined.times"
}

download-resources-combined "17861-6" "8.67" "10"
download-resources-combined "17861-6" "9.35" "50"
download-resources-combined "17861-6" "10.2" "100"

download-resources-combined-subject() {
  CODE="$1"
  VALUE="$2"
  PERCENT="$3"

  echo "Downloading Observations with code $CODE, $PERCENT percent hits, combined search param and _elements=subject..."
  download-resources-raw "code-value-quantity=http://loinc.org|$CODE\$lt$VALUE|http://unitsofmeasure.org|$UNIT&_elements=subject" "download-$CODE-value-$PERCENT-percent-combined-subject.times"
}

download-resources-combined-subject "17861-6" "8.67" "10"
download-resources-combined-subject "17861-6" "9.35" "50"
download-resources-combined-subject "17861-6" "10.2" "100"
