#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"
. "$script_dir/evaluate-measure-util.sh"

# Builds the Parameters body of a POST $evaluate-measure request. Besides the
# usual periodStart, periodEnd and measure inputs it sets the `parameters` input
# to a nested Parameters resource with one string-valued CQL parameter.
#
# $1 - the measure URI
# $2 - the name of the CQL parameter to set
# $3 - the value of the CQL parameter to set
parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "periodStart",
      "valueDate": "2000"
    },
    {
      "name": "periodEnd",
      "valueDate": "2030"
    },
    {
      "name": "measure",
      "valueString": "urn:uuid:$1"
    },
    {
      "name": "parameters",
      "resource": {
        "resourceType": "Parameters",
        "parameter": [
          {
            "name": "$2",
            "valueString": "$3"
          }
        ]
      }
    }
  ]
}
END
}

evaluate_measure() {
  parameters "$2" "$3" "$4" | curl -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1/Measure/\$evaluate-measure"
}

base="http://localhost:8080/fhir"
name="$1"
param_name="$2"
param_value="$3"
expected_count="$4"

measure_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$measure_uri" "$name" | transact "$base" > /dev/null

report=$(evaluate_measure "$base" "$measure_uri" "$param_name" "$param_value")
count=$(echo "$report" | jq -r ".group[0].population[0].count")

if [ "$count" = "$expected_count" ]; then
  echo "✅ count ($count) equals the expected count for $param_name = $param_value"
else
  echo "🆘 count ($count) != $expected_count for $param_name = $param_value"
  echo "Report:"
  echo "$report" | jq .
  exit 1
fi
