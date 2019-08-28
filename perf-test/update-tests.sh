#!/usr/bin/env bash

RATE=1000
ID_START=0
DURATION=60

echo "Run each test for ${DURATION} seconds with a rate of ${RATE} requests/second."
echo "Start with ID ${ID_START}"
echo ""

echo "Patient Update Tests:"
cat patient-update.json | \
jq -cM --argjson start ${ID_START} --argjson rate ${RATE} --argjson duration ${DURATION} \
  '. as $request | range($start; $start + $rate * $duration) | tostring as $id | $request | .url += $id | .body.id = $id | .body |= @base64' | \
vegeta attack -rate=${RATE} -format=json -duration=${DURATION}s | \
vegeta report


echo "Condition Update Tests:"
cat condition-update.json | \
jq -cM --argjson start ${ID_START} --argjson rate ${RATE} --argjson duration ${DURATION} \
  '. as $request | range($start; $start + $rate * $duration) | tostring as $id | $request | .body.id = $id | .url += $id | .body.subject.reference += $id | .body |= @base64' | \
vegeta attack -rate=${RATE} -format=json -duration=${DURATION}s | \
vegeta report


echo "Observation Update Tests:"
cat observation-update.json | \
jq -cM --argjson start ${ID_START} --argjson rate ${RATE} --argjson duration ${DURATION} \
  '. as $request | range($start; $start + $rate * $duration) | tostring as $id | $request | .body.id = $id | .url += $id | .body.subject.reference += $id | .body |= @base64' | \
vegeta attack -rate=${RATE} -format=json -duration=${DURATION}s | \
vegeta report


echo "Specimen Update Tests:"
cat specimen-update.json | \
jq -cM --argjson start ${ID_START} --argjson rate ${RATE} --argjson duration ${DURATION} \
  '. as $request | range($start; $start + $rate * $duration) | tostring as $id | $request | .body.id = $id | .url += $id | .body.subject.reference += $id | .body |= @base64' | \
vegeta attack -rate=${RATE} -format=json -duration=${DURATION}s | \
vegeta report
