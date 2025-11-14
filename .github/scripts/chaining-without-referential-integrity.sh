#!/bin/bash -e

base="http://localhost:8080/fhir"

curl -sXPUT -d '{"resourceType": "Observation", "id": "0", "subject": {"reference": "Patient/0"}}' -H 'Content-Type: application/fhir+json' "$base/Observation/0" > /dev/null
curl -sXPUT -d '{"resourceType" : "Patient", "id": "0", "gender": "male"}' -H 'Content-Type: application/fhir+json' "$base/Patient/0" > /dev/null

result="$(curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/Observation?patient.gender=male&_summary=count" | jq -r '.total')"

if [ "$result" = "1" ]; then
  echo "✅ chaining works"
else
  echo "🆘 chaining doesn't work"
  exit 1
fi
