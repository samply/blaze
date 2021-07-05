#!/bin/bash -e

BASE=http://localhost:8080/fhir
TOTAL_COUNT=$(curl -s "${BASE}/Observation?_summary=count" | jq -r .total)
LAB_COUNT=$(curl -s "${BASE}/Observation?_profile=http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab&_summary=count" | jq -r .total)

if [ $LAB_COUNT -lt $TOTAL_COUNT ]; then
  echo "Success: lab count ($LAB_COUNT) < total count ($TOTAL_COUNT)"
  exit 0
else
  echo "Fail: lab count ($LAB_COUNT) !< total count ($TOTAL_COUNT)"
  exit 1
fi
