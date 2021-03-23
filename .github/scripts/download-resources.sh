#!/usr/bin/env -S bash -e

BASE=http://localhost:8080/fhir
TYPE=$1
EXPECTED_SIZE=$(curl -s "${BASE}/${TYPE}?_summary=count" | jq -r .total)

./blazectl --server $BASE download -t $TYPE -o ${TYPE}.ndjson

SIZE=$(wc -l ${TYPE}.ndjson | xargs | cut -d ' ' -f1)

if [ $EXPECTED_SIZE = $SIZE ]; then
  echo "Success: download size matches"
  exit 0
else
  echo "Fail: download size was ${SIZE} but should be ${EXPECTED_SIZE}"
  exit 1
fi
