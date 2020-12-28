#!/usr/bin/env bash

ID_START=0
ID_END=3000


echo "Patient Read Tests:"
cat patient-read.json | \
jq -cM --argjson id_start ${ID_START} --argjson id_end ${ID_END} \
  '. as $request | range($id_start; $id_end) | tostring as $id | $request | .url += $id' | \
vegeta attack -rate=3000 -format=json -duration=30s | \
vegeta report
