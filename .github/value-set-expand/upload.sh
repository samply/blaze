#!/bin/bash -e

FILENAME=$1
BASE="http://localhost:8080/fhir"

echo "Upload $FILENAME"

RESOURCE_TYPE="$(jq -r .resourceType "$FILENAME")"
if [[ "$RESOURCE_TYPE" =~ ValueSet|CodeSystem ]]; then
  URL="$(jq -r .url "$FILENAME")"
  if [[ "$URL" =~ http://unitsofmeasure.org|http://snomed.info/sct|http://loinc.org|urn:ietf:bcp:13 ]]; then
    echo "Skip creating the code system or value set $URL which is internal in Blaze"
  else
    curl -sf -H "Content-Type: application/fhir+json" -H "Prefer: return=minimal" -d @"$FILENAME" "$BASE/$RESOURCE_TYPE"
  fi
fi
