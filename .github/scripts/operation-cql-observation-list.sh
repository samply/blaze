#!/bin/bash -e

#
# Queries all pain severity (72514-3) observations and returns the patient ID,
# the issued time and the score.
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
      "valueString": "[Observation: Code {system: 'http://loinc.org', code: '72514-3'}] O return {patientId: First([Patient] P where P.id = Last(Split(O.subject.reference, '/')) return P.identifier.where(system = 'http://hl7.org/fhir/sid/us-ssn').value.first()), issued: O.issued, score: O.value.value}"
    }
  ]
}
END
}

result="$(curl -sfH 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' -d "$(parameters)" "$base/\$cql")"

test "result resource type" "$(echo "$result" | jq -r '.resourceType')" "Parameters"
test "number of return parameters" "$(echo "$result" | jq -r '.parameter | length')" "1368"
test "result CSV" "$(echo "$result" | jq -r '.parameter[] | [(.part[] | select(.name == "patientId" ).valueString), (.part[] | select(.name == "issued" ).valueInstant), (.part[] | select(.name == "score" ).valueDecimal)] | @csv' | sort | head -n 10)" "$(cat "$script_dir/operation-cql/observation-list.csv")"
