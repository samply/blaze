#!/bin/bash -e

BASE=http://localhost:8080/fhir
LAB_COUNT=$(curl -s "$BASE/Observation?_profile=http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab&_summary=count" | jq -r .total)

if [ "$LAB_COUNT" = "27218" ]; then
  echo "Success: lab count ($LAB_COUNT) equals the expected count"
else
  echo "Fail: lab count ($LAB_COUNT) != 27218"
  exit 1
fi
