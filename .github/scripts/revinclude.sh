#!/bin/bash -e

base="http://localhost:8080/fhir"
expected_num_patients=$(curl -s "$base/Patient?_summary=count" | jq -r .total)
expected_num_observations=$(curl -s "$base/Observation?_summary=count" | jq -r .total)
expected_num_conditions=$(curl -s "$base/Condition?_summary=count" | jq -r .total)
expected_num_encounters=$(curl -s "$base/Encounter?_summary=count" | jq -r .total)
expected_num_procedures=$(curl -s "$base/Procedure?_summary=count" | jq -r .total)

blazectl --server $base download Patient -q '_count=1&_revinclude=Observation:subject&_revinclude=Condition:subject&_revinclude=Procedure:subject&_revinclude=Encounter:subject' -o output.ndjson

actual_num_patients=$(jq -r .resourceType output.ndjson | grep -c Patient)
actual_num_observations=$(jq -r .resourceType output.ndjson | grep -c Observation)
actual_num_conditions=$(jq -r .resourceType output.ndjson | grep -c Condition)
actual_num_encounters=$(jq -r .resourceType output.ndjson | grep -c Encounter)
actual_num_procedures=$(jq -r .resourceType output.ndjson | grep -c Procedure)

rm output.ndjson

if [ "$expected_num_patients" != "$actual_num_patients" ]; then
  echo "🆘 Patient download size was ${actual_num_patients} but should be ${expected_num_patients}"
  exit 1
elif [ "$expected_num_observations" != "$actual_num_observations" ]; then
  echo "🆘 Observation download size was ${actual_num_observations} but should be ${expected_num_observations}"
  exit 1
elif [ "$expected_num_conditions" != "$actual_num_conditions" ]; then
  echo "🆘 Condition download size was ${actual_num_conditions} but should be ${expected_num_conditions}"
  exit 1
elif [ "$expected_num_encounters" != "$actual_num_encounters" ]; then
  echo "🆘 Encounter download size was ${actual_num_encounters} but should be ${expected_num_encounters}"
  exit 1
elif [ "$expected_num_procedures" != "$actual_num_procedures" ]; then
  echo "🆘 Procedure download size was ${actual_num_procedures} but should be ${expected_num_procedures}"
  exit 1
else
  echo "✅ all download sizes match"
fi
