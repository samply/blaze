#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"
. "$script_dir/evaluate-measure-util.sh"

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
      "name": "reportType",
      "valueCode": "subject-list"
    },
    {
      "name": "measure",
      "valueString": "urn:uuid:$1"
    }
  ]
}
END
}

evaluate_measure() {
  parameters "$2" | curl -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1/Measure/\$evaluate-measure"
}

base="http://localhost:8080/fhir"
name=$1
expected_count=$2

measure_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$measure_uri" "$name" | transact "$base" > /dev/null

report=$(evaluate_measure "$base" "$measure_uri")
count=$(echo "$report" | jq -r ".group[0].population[0].count")

if [ "$count" = "$expected_count" ]; then
  echo "✅ count ($count) equals the expected count"
else
  echo "🆘 count ($count) != $expected_count"
  echo "Report:"
  echo "$report" | jq .
  exit 1
fi

if [ "0" = "$expected_count" ]; then
  exit 0
fi

list_id=$(echo "$report" | jq -r '.group[0].population[0].subjectResults.reference | split("/")[1]')
patient_ids=$(fetch_patient_ids "$base" "$list_id")
id_count=$(echo "$patient_ids" | sed '/^$/d' | sort -u | wc -l | xargs)

if [ "$id_count" = "$expected_count" ]; then
  echo "✅ downloaded patient count ($id_count) equals the expected count"
else
  echo "🆘 downloaded patient count ($id_count) != $expected_count"
  echo "Patient IDs:"
  echo "$patient_ids"
  exit 1
fi
