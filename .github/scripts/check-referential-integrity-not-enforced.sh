#!/bin/bash -e

enforced=$(curl -s http://localhost:8080/fhir/metadata | jq -r 'isempty(.rest[].resource[].referencePolicy[] | select(. == "enforced")) | not')

if [ "false" = "$enforced" ]; then
  echo "✅"
else
  echo "Fail 😞"
  exit 1
fi
