#!/bin/bash -e

java -jar validator_cli.jar -txTests -version 4.0.1 \
  -tx http://localhost:8080/fhir \
  -source fhir-tx-ecosystem-ig/tests \
  -output .github/terminology-tests/output \
  -mode flat
