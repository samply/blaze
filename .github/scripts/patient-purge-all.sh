#!/bin/bash
set -euo pipefail

base="http://localhost:8080/fhir"

curl -sfH 'Accept: application/fhir+json' "$base/Patient?_count=10000&_elements=id" | \
  jq -r '.entry[].resource.id' | \
  xargs -P 4 -I {} curl -s -X POST "$base/Patient/{}/\$purge" -o /dev/null
