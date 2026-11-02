#!/bin/bash
set -euo pipefail

# runs the $re-index operation for the clinical-code search parameter, waits
# for the async response and inspects the resulting job

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

start_epoch="$(date +"%s")"

elapsed() {
  local epoch="$(date +"%s")"
  echo $((epoch - start_epoch))
}

base="http://localhost:8080/fhir"
search_param_url="http://hl7.org/fhir/SearchParameter/clinical-code"

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "search-param-url",
      "valueCanonical": "$search_param_url"
    }
  ]
}
END
}

headers=$(parameters | execute_operation "$base/\$re-index" -o /dev/null -D -)

test "status code" "$(echo "$headers" | head -1 | cut -d' ' -f2)" "202"

status_url=$(echo "$headers" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(elapsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$status_url")" != "200") ]]; do
  sleep 1
done

response_bundle="$(curl -s -H 'Accept: application/fhir+json' "$status_url")"

test "response bundle type" "$(echo "$response_bundle" | jq -r .type)" "batch-response"
test "response status" "$(echo "$response_bundle" | jq -r '.entry[0].response.status')" "200"

# the underlying job is available via the admin API
job=$(curl -sfH 'Accept: application/fhir+json' "$base/__admin/Task?_profile=https://blaze-server.org/fhir/StructureDefinition/ReIndexJob&_sort=-_lastUpdated&_count=1" | jq '.entry[0].resource')

test "job status" "$(echo "$job" | jq -r '.status')" "completed"
test "job search-param-url" "$(echo "$job" | jq -r '.input[0].valueCanonical')" "$search_param_url"

output_uri="https://blaze-server.org/fhir/CodeSystem/ReIndexJobOutput"

output_expr() {
  echo ".output[] | select(.type.coding[] | select(.system == \"$output_uri\" and .code == \"$1\"))"
}

total_resources="$(echo "$job" | jq "$(output_expr "total-resources") | .valueUnsignedInt")"

test "job total-resources positive" "$(echo "$total_resources" | jq '. > 0')" "true"
test "job resources-processed" "$(echo "$job" | jq "$(output_expr "resources-processed") | .valueUnsignedInt")" "$total_resources"
