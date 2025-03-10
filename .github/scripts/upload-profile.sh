#!/bin/bash -e

BASE="http://localhost:8080/fhir"

curl -s -f -H 'Content-Type: application/fhir+json' -H 'Prefer: return=minimal' -d @"$1" "$BASE/StructureDefinition"
