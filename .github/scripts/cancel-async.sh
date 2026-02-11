#!/bin/bash -e

#
# This script verifies the correct cancellation of async requests.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
headers=$(curl -s -H 'Prefer: respond-async' -H 'Accept: application/fhir+json' -o /dev/null -D - "$base/Observation?date=gt2000&date=lt2100&_summary=count")
status_url=$(echo "$headers" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

response=$(curl -s -XDELETE -w "%{response_code}" "$status_url")
status_code="${response: -3}"
if [ "$status_code" = "202" ]; then
  echo "✅ the cancel status code is 202"
else
  echo "🆘 the cancel status code is $status_code, expected 202, diagnostics: $(echo "${response%???}" | jq -r '.issue[0].diagnostics')"
  exit 1
fi

response=$(curl -s -w '%{response_code}' "$status_url")
test "status code after cancel" "${response: -3}" "404"
test "after cancel resource type" "$(echo "${response%???}" | jq -r '.resourceType')" "OperationOutcome"
test "after cancel issue code" "$(echo "${response%???}" | jq -r '.issue[0].code')" "not-found"

diagnostics="$(echo "${response%???}" | jq -r '.issue[0].diagnostics')"
if [[ "$diagnostics" =~ The\ asynchronous\ request\ with\ id\ \`[A-Z0-9]+\`\ is\ cancelled. ]]; then
    echo "✅ the diagnostics message is right"
else
    echo "🆘 the diagnostics message is $diagnostics, expected /The asynchronous request with id \`[A-Z0-9]\+\` is cancelled./"
fi
