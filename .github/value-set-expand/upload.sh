#!/bin/bash -e

FILENAME=$1
BASE="http://localhost:8080/fhir"

echo "Upload $FILENAME"

RESOURCE_TYPE=$(jq -r .resourceType "$FILENAME")
if [[ "$RESOURCE_TYPE" =~ ValueSet|CodeSystem ]]; then
  curl -sf -H "Content-Type: application/fhir+json" -H "Prefer: return=minimal" -d @"$FILENAME" "$BASE/$RESOURCE_TYPE"
fi
