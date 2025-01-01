#!/bin/bash

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

# remove test cases that need the R5 representation of the ValueSet resource
jq -f "$SCRIPT_DIR/remove-r5-tests.jq" fhir-tx-ecosystem-ig/tests/test-cases.json > fhir-tx-ecosystem-ig/tests/test-cases-new.json
mv fhir-tx-ecosystem-ig/tests/test-cases-new.json fhir-tx-ecosystem-ig/tests/test-cases.json

java -jar validator_cli.jar -txTests -version 4.0.1 \
  -tx http://localhost:8080/fhir \
  -source fhir-tx-ecosystem-ig/tests \
  -output .github/terminology-tests/output \
  -mode flat \
  -filter parameters-expand

exit $((! $?))
