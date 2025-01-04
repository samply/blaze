#!/bin/bash

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
TESTS="fhir-tx-ecosystem-ig/tests"

# remove test cases that need the R5 representation of the ValueSet resource
jq -f "$SCRIPT_DIR/remove-r5-tests.jq" "$TESTS/test-cases.json" > "$TESTS/test-cases-new.json"
mv "$TESTS/test-cases-new.json" "$TESTS/test-cases.json"

java -jar validator_cli.jar -txTests -version 4.0.1 \
  -tx http://localhost:8080/fhir \
  -source "$TESTS" \
  -output .github/terminology-tests/output \
  -mode flat \
  -filter parameters-expand

exit $((! $?))
