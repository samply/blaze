#!/bin/bash -e

BASE="http://localhost:8080/fhir"

for ID in $(curl -s "$BASE/Patient?_count=10000&_elements=id" | jq -r '.entry[].resource.id'); do
  curl -s -XPOST "$BASE/Patient/$ID/\$purge" -o /dev/null
done
