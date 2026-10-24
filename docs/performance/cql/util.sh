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

# Evaluates the measure of the CQL file with the given name and prints one
# line of statistics. Prints a header with the Bloom filter ratio unless the
# second argument is `false`.
search() {
  local file="$1"
  local output_header="${2:-true}"
  local base="http://localhost:8080/fhir"

  local patient_total
  patient_total="$(curl -sH 'Accept: application/fhir+json' "$base/Patient?_summary=count" | jq -r .total)"

  if [ "true" = "$output_header" ]; then
    echo "Counting Patients with criteria from $file..."
  fi
  local report
  report="$(blazectl --server "$base" evaluate-measure --force-sync "$script_dir/$file.yml" 2> /dev/null)"

  if [ "true" = "$output_header" ]; then
    echo "Bloom filter ratio: $(echo "$report" | jq -rf "$script_dir/bloom-filter-ratio.jq")"
  fi

  local count
  count="$(echo "$report" | jq -r '.group[0].population[0].count')"

  local times_file
  times_file="$(mktemp)"

  sleep 10
  # the first run is the warmup which isn't taken for the statistics
  local i repeats
  repeats="$(repeats)"
  for ((i = 0; i <= repeats; i++)); do
    sleep 1
    blazectl --server "$base" evaluate-measure --force-sync "$script_dir/$file.yml" 2> /dev/null |\
      jq -rf "$script_dir/duration.jq" >> "$times_file"
    start-profiling-after-warmup "$i"
  done

  calc-cql-print-stats "$times_file" "$patient_total" "$count"
  rm "$times_file"
}

# Runs `search` for the CQL file with the given name the given number of times.
# Prints the header only the first time.
search-repeatedly() {
  local file="$1"
  local times="$2"

  search "$file"
  local i
  for ((i = 1; i < times; i++)); do
    search "$file" false
  done
}
