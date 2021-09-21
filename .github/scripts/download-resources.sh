#!/bin/bash -e

BASE=http://localhost:8080/fhir
TYPE=$1
EXPECTED_SIZE=$(curl -s "${BASE}/${TYPE}?_summary=count" | jq -r .total)

FILE_NAME=$(uuidgen)
blazectl --server $BASE download "$TYPE" -o "$FILE_NAME".ndjson

SIZE=$(wc -l "$FILE_NAME".ndjson | xargs | cut -d ' ' -f1)

if [ "$EXPECTED_SIZE" = "$SIZE" ]; then
  echo "Success: download size matches"
else
  echo "Fail: download size was ${SIZE} but should be ${EXPECTED_SIZE}"
  exit 1
fi
