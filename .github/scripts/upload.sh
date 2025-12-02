#!/bin/bash -e

filename=$1
base="http://localhost:8080/fhir"

resource_type="$(jq -r .resourceType "$filename")"

echo "Upload $filename"
curl -sf -H "Content-Type: application/fhir+json" -H "Prefer: return=minimal" -d @"$filename" "$base/$resource_type"
