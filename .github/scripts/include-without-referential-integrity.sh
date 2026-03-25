#!/bin/bash -e
set -o pipefail

# This test creates two observations both referencing different patients where
# only one of the patients exists.
#
# An observation query including the subjects of the observations is executed.
# The expected result contains both observations and the single patient. It's
# important that the dead reference doesn't result in an error. It is just
# ignored.

base="http://localhost:8080/fhir"

curl -sf -XPUT -d '{"resourceType": "Observation", "id": "0", "subject": {"reference": "Patient/0"}}' -H 'Content-Type: application/fhir+json' "$base/Observation/0" > /dev/null
curl -sf -XPUT -d '{"resourceType": "Observation", "id": "1", "subject": {"reference": "Patient/1"}}' -H 'Content-Type: application/fhir+json' "$base/Observation/1" > /dev/null
curl -sf -XPUT -d '{"resourceType" : "Patient", "id": "0"}' -H 'Content-Type: application/fhir+json' "$base/Patient/0" > /dev/null

result=$(curl -sfH 'Accept: application/fhir+json' "$base/Observation?_include=Observation:subject" | jq -r '[.entry[].search.mode] | join(",")')

if [ "$result" = "match,match,include" ]; then
  echo "✅ include works"
else
  echo "🆘 include doesn't work"
  exit 1
fi
