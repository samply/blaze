#!/usr/bin/env bash

ID_START=42000
ID_END=51000


echo "Patient Update Tests:"
for (( ID=$ID_START; ID<$ID_END; ID++))
do
  cat patient-update.json | \
  jq -cM --arg id ${ID} '.url += $id | .body.id = $id | .body |= @base64'
done | \
vegeta attack -rate=300 -format=json -duration=30s | \
vegeta report


echo "Condition Update Tests:"
for (( ID=$ID_START; ID<$ID_END; ID++))
do
  cat condition-update.json | \
  jq -cM --arg id ${ID} '.url += $id | .body.id = $id | .body.subject.reference += $id | .body |= @base64'
done | \
vegeta attack -rate=300 -format=json -duration=30s | \
vegeta report


echo "Observation Update Tests:"
for (( ID=$ID_START; ID<$ID_END; ID++))
do
  cat observation-update.json | \
  jq -cM --arg id ${ID} '.url += $id | .body.id = $id | .body.subject.reference += $id | .body |= @base64'
done | \
vegeta attack -rate=300 -format=json -duration=30s | \
vegeta report


echo "Specimen Update Tests:"
for (( ID=$ID_START; ID<$ID_END; ID++))
do
  cat specimen-update.json | \
  jq -cM --arg id ${ID} '.url += $id | .body.id = $id | .body.subject.reference += $id | .body |= @base64'
done | \
vegeta attack -rate=300 -format=json -duration=30s | \
vegeta report
