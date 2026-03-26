#!/bin/bash
set -euo pipefail

base="http://localhost:8080/fhir"

curl -sfH 'Content-Type: application/fhir+json' -H 'Prefer: return=minimal' -d @"$1" "$base/StructureDefinition"
