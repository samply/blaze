#!/usr/bin/env bash

ID_START=0
ID_END=3000


echo "Patient Read Tests:"
for (( ID=$ID_START; ID<$ID_END; ID++))
do
  cat patient-read.json | \
  jq -cM --arg id ${ID} '.url += $id'
done | \
vegeta attack -rate=100 -lazy -format=json -duration=30s | \
vegeta report
