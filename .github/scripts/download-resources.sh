#!/usr/bin/env -S bash -e

TYPE=$1
EXPECTED_SIZE=$2

./blazectl --server http://localhost:8080/fhir download -t $TYPE -o ${TYPE}.ndjson

SIZE=$(wc -l ${TYPE}.ndjson | xargs | cut -d ' ' -f1)

if [ $EXPECTED_SIZE = $SIZE ]; then
  echo "Success: Download size matches"
  exit 0
else
  echo "Fail: Download size was ${SIZE} but should be ${EXPECTED_SIZE}"
  exit 1
fi
