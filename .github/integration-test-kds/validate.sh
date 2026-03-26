#!/bin/bash
set -euo pipefail

base="http://localhost:8084"

curl -s -H 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' \
  -d @.github/test-data/kds-testdata-2024.0.1/tx/Bundle-mii-exa-test-data-bundle.json \
  "$base/validateResource" |\
  jq -r '.issue[] | select(.severity == "error").details.text' |\
  sort |\
  uniq -c
