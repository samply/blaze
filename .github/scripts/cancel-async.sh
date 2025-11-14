#!/bin/bash -e

#
# This script verifies the correct cancellation of async requests.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
headers=$(curl -s -H 'Prefer: respond-async' -H 'Accept: application/fhir+json' -o /dev/null -D - "$base/Observation?date=lt2100&_summary=count")
status_url=$(echo "$headers" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

status_code=$(curl -s -XDELETE -o /dev/null -w '%{response_code}' "$status_url")
test "cancel status code" "$status_code" "202"

test "status code after cancel" "$(curl -s -o /dev/null -w '%{response_code}' "$status_url")" "404"
response="$(curl -s "$status_url")"
test "after cancel resource type" "$(echo "$response" | jq -r .resourceType)" "OperationOutcome"
test "after cancel issue code" "$(echo "$response" | jq -r '.issue[0].code')" "not-found"

diagnostics="$(curl -s -H 'Accept: application/fhir+json' "$status_url" | jq -r '.issue[0].diagnostics')"
if [[ "$diagnostics" =~ The\ asynchronous\ request\ with\ id\ \`[A-Z0-9]+\`\ is\ cancelled. ]]; then
    echo "✅ the diagnostics message is right"
else
    echo "🆘 the diagnostics message is $diagnostics, expected /The asynchronous request with id \`[A-Z0-9]\+\` is cancelled./"
fi
