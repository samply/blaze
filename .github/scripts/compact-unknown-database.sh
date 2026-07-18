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
      "name": "database",
      "valueCode": "foo"
    },
    {
      "name": "column-family",
      "valueCode": "bar"
    }
  ]
}
END
}

outcome="$(curl -sH 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' -d "$(parameters)" "$base/\$compact")"

echo "$outcome"

test "response severity" "$(jq -r '.issue[0].severity' <<<"$outcome")" "error"
test "response code" "$(jq -r '.issue[0].code' <<<"$outcome")" "invalid"
test "response diagnostics" "$(jq -r '.issue[0].diagnostics' <<<"$outcome")" "Invalid value for parameter \`database\`. Should be one of \`index\`, \`transaction\` or \`resource\` but was \`foo\`."
