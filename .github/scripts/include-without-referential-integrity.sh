#!/bin/bash -e

BASE="http://localhost:8080/fhir"

curl -sXPUT -d '{"resourceType": "Observation", "id": "0", "subject": {"reference": "Patient/0"}}' -H 'Content-Type: application/fhir+json' "$BASE/Observation/0" > /dev/null
curl -sXPUT -d '{"resourceType" : "Patient", "id": "0"}' -H 'Content-Type: application/fhir+json' "$BASE/Patient/0" > /dev/null

RESULT=$(curl -s "$BASE/Observation?_include=Observation:subject" | jq -r '.entry[].search.mode' | tr '\n' '|')

if [ "$RESULT" = "match|include|" ]; then
  echo "OK 👍: include works"
else
  echo "Fail 😞: include doesn't work"
  exit 1
fi
