#!/bin/bash

java -jar validator_cli.jar txTests -test-version 1.8.0 \
  -tx http://localhost:8080/fhir \
  -output .github/terminology-tests/output/simple-expand \
  -mode flat \
  -filter simple-expand
