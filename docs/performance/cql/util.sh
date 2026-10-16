#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../fhir-search/util.sh"

calc-cql-print-stats() {
  local times_file="$1"
  local patient_count="$2"
  local count="$3"

  local stats="$(calc-avg "$times_file")"
  local avg=$(echo "$stats" | jq .avg)
  local stddev=$(echo "$stats" | jq .stddev)

  awk -v patient_count="$patient_count" -v count="$count" -v avg="$avg" -v stddev="$stddev" 'BEGIN {
    # the avg patients evaluated per second
    patients_per_sec = patient_count / avg

    # shorten the count
    if (count > 1000000) {
      human_count = sprintf("%4.1f M", count / 1000000)
    } else if (count > 1000) {
      human_count = sprintf("%4.0f k", count / 1000)
    } else {
      human_count = sprintf("%6.0f", count)
    }

    # shorten the patients per second
    if (patients_per_sec > 1000000) {
      human_patients_per_sec = sprintf("%2.3f M", patients_per_sec / 1000000)
    } else if (patients_per_sec > 1000) {
      human_patients_per_sec = sprintf("%5.1f k", patients_per_sec / 1000)
    } else {
      human_patients_per_sec = sprintf("%6.0f", patients_per_sec)
    }

    # non-human output likely useful in the future
    # printf "%d,%.4f,%.4f,%.2f\n", count, avg, stddev, patients_per_sec

    printf "| %s | %8.2f | %6.3f | %s |\n", human_count, avg, stddev, human_patients_per_sec
  }'
}
