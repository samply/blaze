#!/bin/bash
set -euo pipefail

# Takes a LOINC `code`, a `count` and the `number` of requests to issue.
#
# Issues `number` async `_summary=count` requests concurrently. Every request
# creates a job and every job is assigned a job number that is incremented
# using an Observation as atomic counter. Concurrently created jobs compete for
# that counter.
#
# Tests that all requests are accepted, that each of them creates a job of its
# own and that the total value in each result bundle equals to `count`.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

start_epoch="$(date +"%s")"

eclipsed() {
  local epoch
  epoch="$(date +"%s")"
  echo $((epoch - start_epoch))
}

base="http://localhost:8080/fhir"
code="$1"
count="$2"
number="$3"

dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT

# issue all requests concurrently
for i in $(seq 1 "$number"); do
  curl -s -H 'Prefer: respond-async' -H 'Accept: application/fhir+json' -o /dev/null -D "$dir/headers-$i" -w '%{response_code}' "$base/Observation?code=http://loinc.org|$code&_summary=count" > "$dir/status-code-$i" &
done

wait

for i in $(seq 1 "$number"); do
  test "status code of request $i" "$(cat "$dir/status-code-$i")" "202"

  status_url=$(grep -i content-location "$dir/headers-$i" | tr -d '\r' | cut -d: -f2- | xargs)
  test_non_empty "status URL of request $i" "$status_url"
  echo "$status_url" >> "$dir/status-urls"
done

test "number of jobs created" "$(sort -u "$dir/status-urls" | wc -l | xargs)" "$number"

while read -r status_url; do
  # wait for response available
  while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$status_url")" != "200") ]]; do
    sleep 0.1
  done

  response_bundle="$(curl -s -H 'Accept: application/fhir+json' "$status_url")"

  test "response bundle type" "$(echo "$response_bundle" | jq -r .type)" "batch-response"

  result_bundle="$(echo "$response_bundle" | jq '.entry[0].resource')"

  test "result bundle type" "$(echo "$result_bundle" | jq -r .type)" "searchset"
  test "result total" "$(echo "$result_bundle" | jq -r .total)" "$count"
done < "$dir/status-urls"
