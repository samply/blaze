#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"
. "$script_dir/evaluate-measure-util.sh"

evaluate_measure() {
  curl -s "$1/Measure/\$evaluate-measure?measure=urn:uuid:$2&periodStart=2000&periodEnd=2030"
}

base="http://localhost:8080/fhir"
name="$1"
expected_count="$2"

measure_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$measure_uri" "$name" | transact "$base" > /dev/null

report=$(evaluate_measure "$base" "$measure_uri")
count=$(echo "$report" | jq -r ".group[0].population[0].count")

if [ "$count" = "$expected_count" ]; then
  echo "✅ count ($count) equals the expected count"
else
  echo "🆘 count ($count) != $expected_count"
  echo "Report:"
  echo "$report" | jq .
  exit 1
fi
