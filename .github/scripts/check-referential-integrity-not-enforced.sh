#!/bin/bash -e

ENFORCED=$(curl -s http://localhost:8080/fhir/metadata | jq -r 'isempty(.rest[].resource[].referencePolicy[] | select(. == "enforced")) | not')

if [ "false" = "$ENFORCED" ]; then
  echo "Success"
else
  echo "Fail"
  exit 1
fi
