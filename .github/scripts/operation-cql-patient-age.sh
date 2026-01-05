#!/bin/bash -e

#
# Queries all patients and returns the ID and the birth date.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X79746011X"
patient_id=$(curl -s "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "subject",
      "valueString": "Patient/$patient_id"
    },
    {
      "name": "expression",
      "valueString": "AgeInYearsAt(@2026-01-01)"
    }
  ]
}
END
}

result="$(curl -sfH 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' -d "$(parameters)" "$base/\$cql")"

test "result resource type" "$(echo "$result" | jq -r '.resourceType')" "Parameters"
test "number of return parameters" "$(echo "$result" | jq -r '.parameter | length')" "1"
test "age" "$(echo "$result" | jq -r '.parameter[0].valueInteger')" "80"
