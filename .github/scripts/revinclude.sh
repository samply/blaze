#!/bin/bash -e

BASE=http://localhost:8080/fhir
EXPECTED_NUM_PATIENTS=$(curl -s "${BASE}/Patient?_summary=count" | jq -r .total)
EXPECTED_NUM_OBSERVATIONS=$(curl -s "${BASE}/Observation?_summary=count" | jq -r .total)
EXPECTED_NUM_CONDITIONS=$(curl -s "${BASE}/Condition?_summary=count" | jq -r .total)
EXPECTED_NUM_ENCOUNTERS=$(curl -s "${BASE}/Encounter?_summary=count" | jq -r .total)
EXPECTED_NUM_PROCEDURES=$(curl -s "${BASE}/Procedure?_summary=count" | jq -r .total)

blazectl --server $BASE download Patient -q '_revinclude=Observation:subject&_revinclude=Condition:subject&_revinclude=Procedure:subject&_revinclude=Encounter:subject' -o output.ndjson

ACTUAL_NUM_PATIENTS=$(jq -r .resourceType output.ndjson | grep -c Patient)
ACTUAL_NUM_OBSERVATIONS=$(jq -r .resourceType output.ndjson | grep -c Observation)
ACTUAL_NUM_CONDITIONS=$(jq -r .resourceType output.ndjson | grep -c Condition)
ACTUAL_NUM_ENCOUNTERS=$(jq -r .resourceType output.ndjson | grep -c Encounter)
ACTUAL_NUM_PROCEDURES=$(jq -r .resourceType output.ndjson | grep -c Procedure)

rm output.ndjson

if [ "$EXPECTED_NUM_PATIENTS" != "$ACTUAL_NUM_PATIENTS" ]; then
  echo "Fail: Patient download size was ${ACTUAL_NUM_PATIENTS} but should be ${EXPECTED_NUM_PATIENTS}"
  exit 1
elif [ "$EXPECTED_NUM_OBSERVATIONS" != "$ACTUAL_NUM_OBSERVATIONS" ]; then
  echo "Fail: Observation download size was ${ACTUAL_NUM_OBSERVATIONS} but should be ${EXPECTED_NUM_OBSERVATIONS}"
  exit 1
elif [ "$EXPECTED_NUM_CONDITIONS" != "$ACTUAL_NUM_CONDITIONS" ]; then
  echo "Fail: Condition download size was ${ACTUAL_NUM_CONDITIONS} but should be ${EXPECTED_NUM_CONDITIONS}"
  exit 1
elif [ "$EXPECTED_NUM_ENCOUNTERS" != "$ACTUAL_NUM_ENCOUNTERS" ]; then
  echo "Fail: Encounter download size was ${ACTUAL_NUM_ENCOUNTERS} but should be ${EXPECTED_NUM_ENCOUNTERS}"
  exit 1
elif [ "$EXPECTED_NUM_PROCEDURES" != "$ACTUAL_NUM_PROCEDURES" ]; then
  echo "Fail: Procedure download size was ${ACTUAL_NUM_PROCEDURES} but should be ${EXPECTED_NUM_PROCEDURES}"
  exit 1
else
  echo "Success: all download sizes match"
fi
