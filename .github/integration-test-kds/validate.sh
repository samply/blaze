#!/bin/bash
set -euo pipefail

base="http://localhost:8084"

# The validator has no healthcheck and may still be starting up, so retry on
# connection errors like connection refused or reset.
# Can be removed once https://github.com/medizininformatik-initiative/mii-fhir-validator/issues/7 lands.
curl -s --retry 12 --retry-delay 5 --retry-connrefused --retry-all-errors -H 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' \
  -d @.github/test-data/kds-testdata-2024.0.1/tx/Bundle-mii-exa-test-data-bundle.json \
  "$base/validateResource" |\
  jq -r '.issue[] | select(.severity == "error").details.text' |\
  sort |\
  uniq -c
