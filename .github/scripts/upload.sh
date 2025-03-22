#!/bin/bash -e

FILENAME=$1
BASE="http://localhost:8080/fhir"

RESOURCE_TYPE="$(jq -r .resourceType "$FILENAME")"

echo "Upload $FILENAME"
curl -sf -H "Content-Type: application/fhir+json" -H "Prefer: return=minimal" -d @"$FILENAME" "$BASE/$RESOURCE_TYPE"
