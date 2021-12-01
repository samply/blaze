#!/bin/bash -e

TOTAL=$(curl -s http://localhost:8080/fhir | jq -r .total)

if [ "$1" = "$TOTAL" ]; then
  echo "Success"
else
  echo "Fail: total number of resources was $TOTAL but should be $1"
  exit 1
fi
