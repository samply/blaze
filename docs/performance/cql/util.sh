#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/../fhir-search/util.sh"

calc-cql-print-stats() {
  TIMES_FILE="$1"
  PATIENT_COUNT="$2"
  COUNT="$3"

  STATS="$(calc-avg "$TIMES_FILE")"
  AVG=$(echo "$STATS" | jq .avg)

  # the avg patients evaluated per second
  PATIENTS_PER_SEC=$(echo "scale=2; $PATIENT_COUNT / $AVG" | bc)

  # shorten the count
  if (( $(echo "$COUNT > 1000000" | bc) )); then
    COUNT=$(echo "scale=2; $COUNT / 1000000" | bc)
    COUNT_FORMAT="%4.1f M"
  else
    COUNT=$(echo "scale=2; $COUNT / 1000" | bc)
    COUNT_FORMAT="%4.0f k"
  fi

  # shorten the patients per second
  if (( $(echo "$PATIENTS_PER_SEC > 1000000" | bc) )); then
    PATIENTS_PER_SEC=$(echo "scale=2; $PATIENTS_PER_SEC / 1000000" | bc)
    PATIENTS_PER_SEC_FORMAT="%5.1f M"
  elif (( $(echo "$PATIENTS_PER_SEC > 1000" | bc) )); then
    PATIENTS_PER_SEC=$(echo "scale=2; $PATIENTS_PER_SEC / 1000" | bc)
    PATIENTS_PER_SEC_FORMAT="%5.1f k"
  else
    PATIENTS_PER_SEC_FORMAT="%6.0f"
  fi

  printf "| $COUNT_FORMAT | %8.2f | %6.3f | $PATIENTS_PER_SEC_FORMAT |\n" "$COUNT" "$AVG" "$(echo "$STATS" | jq .stddev)" "$PATIENTS_PER_SEC"
}
