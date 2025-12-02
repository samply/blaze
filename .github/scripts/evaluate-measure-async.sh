#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"
. "$script_dir/evaluate-measure-util.sh"

start_epoch="$(date +"%s")"

eclipsed() {
  local epoch="$(date +"%s")"
  echo $((epoch - start_epoch))
}

evaluate_measure() {
  curl -s -H "Prefer: respond-async" -o /dev/null -D - "$1/Measure/\$evaluate-measure?measure=urn:uuid:$2&periodStart=2000&periodEnd=2030"
}

base="http://localhost:8080/fhir"
name="$1"
expected_count="$2"

measure_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$measure_uri" "$name" | transact "$base" > /dev/null

headers=$(evaluate_measure "$base" "$measure_uri")
status_url=$(echo "$headers" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$status_url")" != "200") ]]; do
  sleep 0.1
done

bundle="$(curl -s -H 'Accept: application/fhir+json' "$status_url")"
count=$(echo "$bundle" | jq -r ".entry[0].resource.group[0].population[0].count")

if [ "$count" = "$expected_count" ]; then
  echo "✅ count ($count) equals the expected count"
else
  echo "🆘 count ($count) != $expected_count"
  echo "Report:"
  echo "$bundle" | jq .
  exit 1
fi
