#!/usr/bin/env bash

BASE="http://localhost:8080/fhir"
NAME="$1"
EXPECTED_COUNT="$2"

REPORT=$(blazectl --server "$BASE" evaluate-measure ".github/scripts/cql/$NAME.yml" 2> /dev/null)
COUNT=$(echo "$REPORT" | jq '.group[0].population[0].count')

if [ "$COUNT" = "$EXPECTED_COUNT" ]; then
  echo "Success: count ($COUNT) equals the expected count"
else
  echo "Fail: count ($COUNT) != $EXPECTED_COUNT"
  exit 1
fi

STRATIFIER_DATA=$(echo "$REPORT" | jq -r '.group[0].stratifier[0].stratum[] | [.value.text, .population[0].count] | @csv')
EXPECTED_STRATIFIER_DATA=$(cat ".github/scripts/cql/$NAME.csv")

if [ "$STRATIFIER_DATA" = "$EXPECTED_STRATIFIER_DATA" ]; then
  echo "Success: stratifier data equals the expected stratifier data"
else
  echo "Fail: stratifier data differs"
  echo "$STRATIFIER_DATA"
  exit 1
fi
