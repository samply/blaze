#!/bin/bash -e

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

start_epoch="$(date +"%s")"

eclipsed() {
  local epoch="$(date +"%s")"
  echo $((epoch - start_epoch))
}

evaluate_measure() {
  parameters "$2" | curl -s -H "Prefer: respond-async,return=representation" -H "Content-Type: application/fhir+json" -d @- -o /dev/null -D - "$1/Measure/\$evaluate-measure"
}

fetch_patients() {
  curl -s "$1/Patient?_list=$2&_count=200"
}

base="http://localhost:8080/fhir"
name=$1
expected_count=$2

measure_uri=$(uuidgen | tr '[:upper:]' '[:lower:]')

create_bundle_library_measure "$measure_uri" "$name" | transact "$base" > /dev/null

headers=$(evaluate_measure "$base" "$measure_uri")
status_url=$(echo "$headers" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$status_url")" != "200") ]]; do
  sleep 0.1
done

bundle="$(curl -s -H 'Accept: application/fhir+json' "$status_url")"
report=$(echo "$bundle" | jq -r ".entry[0].resource")
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
patient_bundle=$(fetch_patients "$base" "$list_id")
id_count=$(echo "$patient_bundle" | jq -r ".entry[].resource.id" | sort -u | wc -l | xargs)

if [ "$id_count" = "$expected_count" ]; then
  echo "✅ downloaded patient count ($id_count) equals the expected count"
else
  echo "🆘 downloaded patient count ($id_count) != $expected_count"
  echo "Patient bundle:"
  echo "$patient_bundle" | jq .
  exit 1
fi
