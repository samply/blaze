#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "search-param-url",
      "valueCanonical": "https://example.com/unknown-search-param"
    }
  ]
}
END
}

status="$(parameters | execute_operation_allow_error "$base/\$re-index" -o /dev/null -w '%{response_code}')"

test "status code" "$status" "400"

outcome="$(parameters | execute_operation_allow_error "$base/\$re-index")"

test "response severity" "$(jq -r '.issue[0].severity' <<<"$outcome")" "error"
test "response code" "$(jq -r '.issue[0].code' <<<"$outcome")" "invalid"
test "response diagnostics" "$(jq -r '.issue[0].diagnostics' <<<"$outcome")" "Invalid value for parameter \`search-param-url\`. Search parameter with URL \`https://example.com/unknown-search-param\` not found."
