#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
start_epoch="$(date +"%s")"
patient_total="$(curl -sH 'Accept: application/fhir+json' "$base/Patient?_summary=count" | jq -r .total)"
file="$1"
output_header="${2:-true}"

if [ "true" = "$output_header" ]; then
  echo "Counting Patients with criteria from $file..."
fi
report="$(blazectl --server "$base" evaluate-measure --force-sync "$script_dir/$file.yml" 2> /dev/null)"

if [ "true" = "$output_header" ]; then
  echo "Bloom filter ratio: $(echo "$report" | jq -rf "$script_dir/bloom-filter-ratio.jq")"
fi

count="$(echo "$report" | jq -r '.group[0].population[0].count')"

sleep 10
for i in {0..3}
do
  sleep 1
  blazectl --server "$base" evaluate-measure --force-sync "$script_dir/$file.yml" 2> /dev/null |\
    jq -rf "$script_dir/duration.jq" >> "$start_epoch-$file.times"
done

calc-cql-print-stats "$start_epoch-$file.times" "$patient_total" "$count"
