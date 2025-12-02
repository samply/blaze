#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../fhir-search/util.sh"

calc-cql-print-stats() {
  local times_file="$1"
  local patient_count="$2"
  local count="$3"

  local stats="$(calc-avg "$times_file")"
  local avg=$(echo "$stats" | jq .avg)

  # the avg patients evaluated per second
  local patients_per_sec=$(echo "scale=2; $patient_count / $avg" | bc)

  # shorten the count
  if (( $(echo "$count > 1000000" | bc) )); then
    local human_count=$(echo "scale=2; $count / 1000000" | bc)
    local human_count_format="%4.1f M"
  elif (( $(echo "$count > 1000" | bc) )); then
    local human_count=$(echo "scale=2; $count / 1000" | bc)
    local human_count_format="%4.0f k"
  else
    local human_count=$count
    local human_count_format="%6.0f"
  fi

  # shorten the patients per second
  if (( $(echo "$patients_per_sec > 1000000" | bc) )); then
    local human_patients_per_sec=$(echo "scale=4; $patients_per_sec / 1000000" | bc)
    local human_patients_per_sec_format="%2.3f M"
  elif (( $(echo "$patients_per_sec > 1000" | bc) )); then
    local human_patients_per_sec=$(echo "scale=2; $patients_per_sec / 1000" | bc)
    local human_patients_per_sec_format="%5.1f k"
  else
    local human_patients_per_sec_format="%6.0f"
  fi

  # non-human output likely useful in the future
  # printf "%d,%.4f,%.4f,%.2f\n" "$count" "$avg" "$(echo "$stats" | jq .stddev)" "$patients_per_sec"

  printf "| $human_count_format | %8.2f | %6.3f | $human_patients_per_sec_format |\n" "$human_count" "$avg" "$(echo "$stats" | jq .stddev)" "$human_patients_per_sec"
}
