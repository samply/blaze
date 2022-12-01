#!/usr/bin/env bash

BASE="http://localhost:8080/fhir"
NAME="$1"
EXPECTED_COUNT="$2"

COUNT=$(blazectl --server "$BASE" evaluate-measure ".github/scripts/cql/$NAME.yml" 2> /dev/null | jq '.group[0].population[0].count')

if [ "$COUNT" = "$EXPECTED_COUNT" ]; then
  echo "Success: count ($COUNT) equals the expected count"
else
  echo "Fail: count ($COUNT) != $EXPECTED_COUNT"
  exit 1
fi
