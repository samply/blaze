#!/bin/bash -e

base="http://localhost:8080/fhir"

curl -s "$base/Patient?_count=10000&_elements=id" | \
  jq -r '.entry[].resource.id' | \
  xargs -P 4 -I {} curl -s -X POST "$base/Patient/{}/\$purge" -o /dev/null
