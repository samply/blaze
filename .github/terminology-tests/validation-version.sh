#!/bin/bash

java -jar validator_cli.jar txTests -test-version 1.9.0 \
  -tx http://localhost:8080/fhir \
  -output .github/terminology-tests/output \
  -mode flat \
  -filter validation-version
