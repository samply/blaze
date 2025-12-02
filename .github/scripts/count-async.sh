#!/bin/bash -e

# Takes a LOINC `code` and `count` and issues am async _summary=count request.
# Tests that the total value in result bundle equals to `count`.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

start_epoch="$(date +"%s")"

eclipsed() {
  local epoch="$(date +"%s")"
  echo $((epoch - start_epoch))
}

base="http://localhost:8080/fhir"
code="$1"
count="$2"
headers=$(curl -s -H 'Prefer: respond-async' -H 'Accept: application/fhir+json' -o /dev/null -D - "$base/Observation?code=http://loinc.org|$code&_summary=count")
status_url=$(echo "$headers" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$status_url")" != "200") ]]; do
  sleep 0.1
done

response_bundle="$(curl -s -H 'Accept: application/fhir+json' "$status_url")"

test "response bundle type" "$(echo "$response_bundle" | jq -r .type)" "batch-response"

result_bundle="$(echo "$response_bundle" | jq '.entry[0].resource')"

test "result bundle type" "$(echo "$result_bundle" | jq -r .type)" "searchset"
test "result total" "$(echo "$result_bundle" | jq -r .total)" "$count"
