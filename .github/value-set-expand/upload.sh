#!/bin/bash -e

filename=$1
base="http://localhost:8080/fhir"

resource_type="$(jq -r .resourceType "$filename")"
if [[ "$resource_type" =~ ValueSet|CodeSystem ]]; then
  url="$(jq -r .url "$filename")"
  if [[ "$url" =~ http://unitsofmeasure.org|http://snomed.info/sct|http://loinc.org|urn:ietf:bcp:13 ]]; then
    echo "Skip creating the code system or value set $url which is internal in Blaze"
  else
    echo "Upload $filename"
    curl -sf -H "Content-Type: application/fhir+json" -H "Prefer: return=minimal" -d @"$filename" "$base/$resource_type"
  fi
fi
