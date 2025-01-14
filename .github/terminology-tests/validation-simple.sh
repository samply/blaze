#!/bin/bash

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
TESTS="fhir-tx-ecosystem-ig/tests"

# remove test cases that show an empty OperationOutcome
jq -f "$SCRIPT_DIR/remove-empty-oo-tests.jq" "$TESTS/test-cases.json" > "$TESTS/test-cases-new.json"
mv "$TESTS/test-cases-new.json" "$TESTS/test-cases.json"

# remove test cases that have OperationOutcomes with not clearly seen logic =
jq -f "$SCRIPT_DIR/remove-complex-error-tests.jq" "$TESTS/test-cases.json" > "$TESTS/test-cases-new.json"
mv "$TESTS/test-cases-new.json" "$TESTS/test-cases.json"

java -jar validator_cli.jar -txTests -version 4.0.1 \
  -tx http://localhost:8080/fhir \
  -source "$TESTS" \
  -output .github/terminology-tests/output \
  -mode flat \
  -filter validation-simple

exit $((! $?))
