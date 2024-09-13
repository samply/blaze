#!/bin/bash -e

BASE="http://localhost:8080/fhir"
PATIENT_ID=$(curl -s "$BASE/Patient?identifier=http://hl7.org/fhir/sid/us-ssn|999-82-5655" | jq -r '.entry[].resource.id')
OBSERVATION_COUNT=$(curl -s "$BASE/Patient/$PATIENT_ID/Observation?_summary=count" | jq .total)

if [ "$OBSERVATION_COUNT" = "1277" ]; then
  echo "✅ lab count ($OBSERVATION_COUNT) equals the expected count"
else
  echo "🆘 lab count ($OBSERVATION_COUNT) != 1277"
  exit 1
fi
