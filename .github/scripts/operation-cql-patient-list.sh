#!/bin/bash -e

#
# Queries all patients and returns the ID and the birth date.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "expression",
      "valueString": "[Patient] P return { id: P.identifier.where(system = 'http://hl7.org/fhir/sid/us-ssn').value.first(), birthDate: P.birthDate }"
    }
  ]
}
END
}

result="$(curl -sfH 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' -d "$(parameters)" "$base/\$cql")"

test "result resource type" "$(echo "$result" | jq -r '.resourceType')" "Parameters"
test "number of return parameters" "$(echo "$result" | jq -r '.parameter | length')" "120"
test "result CSV" "$(echo "$result" | jq -r '.parameter[] | [(.part[] | select(.name == "id" ).valueString), (.part[] | select(.name == "birthDate" ).valueDate)] | @csv' | sort | head -n 10)" "$(cat "$script_dir/operation-cql/patient-list.csv")"
