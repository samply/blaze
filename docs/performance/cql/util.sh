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
    HUMAN_COUNT=$(echo "scale=2; $COUNT / 1000000" | bc)
    HUMAN_COUNT_FORMAT="%4.1f M"
  elif (( $(echo "$COUNT > 1000" | bc) )); then
    HUMAN_COUNT=$(echo "scale=2; $COUNT / 1000" | bc)
    HUMAN_COUNT_FORMAT="%4.0f k"
  else
    HUMAN_COUNT=$COUNT
    HUMAN_COUNT_FORMAT="%6.0f"
  fi

  # shorten the patients per second
  if (( $(echo "$PATIENTS_PER_SEC > 1000000" | bc) )); then
    HUMAN_PATIENTS_PER_SEC=$(echo "scale=4; $PATIENTS_PER_SEC / 1000000" | bc)
    HUMAN_PATIENTS_PER_SEC_FORMAT="%2.3f M"
  elif (( $(echo "$PATIENTS_PER_SEC > 1000" | bc) )); then
    HUMAN_PATIENTS_PER_SEC=$(echo "scale=2; $PATIENTS_PER_SEC / 1000" | bc)
    HUMAN_PATIENTS_PER_SEC_FORMAT="%5.1f k"
  else
    HUMAN_PATIENTS_PER_SEC_FORMAT="%6.0f"
  fi

  # non-human output likely useful in the future
  # printf "%d,%.4f,%.4f,%.2f\n" "$COUNT" "$AVG" "$(echo "$STATS" | jq .stddev)" "$PATIENTS_PER_SEC"

  printf "| $HUMAN_COUNT_FORMAT | %8.2f | %6.3f | $HUMAN_PATIENTS_PER_SEC_FORMAT |\n" "$HUMAN_COUNT" "$AVG" "$(echo "$STATS" | jq .stddev)" "$HUMAN_PATIENTS_PER_SEC"
}
