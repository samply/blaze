#!/bin/bash -e

BASE="http://localhost:8080/fhir"

curl -sXPUT -d '{"resourceType": "Observation", "id": "0", "subject": {"reference": "Patient/0"}}' -H 'Content-Type: application/fhir+json' "$BASE/Observation/0" > /dev/null
curl -sXPUT -d '{"resourceType" : "Patient", "id": "0", "gender": "male"}' -H 'Content-Type: application/fhir+json' "$BASE/Patient/0" > /dev/null

RESULT="$(curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$BASE/Observation?patient.gender=male&_summary=count" | jq -r '.total')"

if [ "$RESULT" = "1" ]; then
  echo "OK 👍: chaining works"
else
  echo "Fail 😞: chaining doesn't work"
  exit 1
fi
