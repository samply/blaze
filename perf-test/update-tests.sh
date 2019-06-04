#!/usr/bin/env bash

RATE=600
ID_START=0


echo "Patient Update Tests:"
cat patient-update.json | \
jq -cM --argjson start ${ID_START} --argjson rate ${RATE} \
  '. as $request | range($start; $start + $rate * 30) | tostring as $id | $request | .url += $id | .body.id = $id | .body |= @base64' | \
vegeta attack -rate=${RATE} -format=json -duration=30s | \
vegeta report


echo "Condition Update Tests:"
cat condition-update.json | \
jq -cM --argjson start ${ID_START} --argjson rate ${RATE} \
  '. as $request | range($start; $start + $rate * 30) | tostring as $id | $request | .body.id = $id | .url += $id | .body.subject.reference += $id | .body |= @base64' | \
vegeta attack -rate=${RATE} -format=json -duration=30s | \
vegeta report


echo "Observation Update Tests:"
cat observation-update.json | \
jq -cM --argjson start ${ID_START} --argjson rate ${RATE} \
  '. as $request | range($start; $start + $rate * 30) | tostring as $id | $request | .body.id = $id | .url += $id | .body.subject.reference += $id | .body |= @base64' | \
vegeta attack -rate=${RATE} -format=json -duration=30s | \
vegeta report


echo "Specimen Update Tests:"
cat specimen-update.json | \
jq -cM --argjson start ${ID_START} --argjson rate ${RATE} \
  '. as $request | range($start; $start + $rate * 30) | tostring as $id | $request | .body.id = $id | .url += $id | .body.subject.reference += $id | .body |= @base64' | \
vegeta attack -rate=${RATE} -format=json -duration=30s | \
vegeta report
