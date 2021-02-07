#!/usr/bin/env sh

TYPE=$1
EXPECTED_SIZE=$2

./blazectl --server http://localhost:8080/fhir download -t $TYPE -o ${TYPE}.ndjson

SIZE=$(wc -c ${TYPE}.ndjson | tr -s "[:space:]" | cut -d ' ' -f2)

if [ $EXPECTED_SIZE = $SIZE ]; then
  echo "Success: Download size matches"
  exit 0
else
  echo "Fail: Download size was ${SIZE} but should be ${EXPECTED_SIZE}"
  exit 1
fi
