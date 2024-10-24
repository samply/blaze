#!/bin/bash -e

BASE="http://localhost:8080/fhir"

curl -s "$BASE/Patient?_count=10000&_elements=id" | \
  jq -r '.entry[].resource.id' | \
  xargs -P 4 -I {} curl -s -X POST "$BASE/Patient/{}/\$purge" -o /dev/null
