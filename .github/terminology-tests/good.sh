#!/bin/bash

java -jar validator_cli.jar txTests -test-version 1.7.9 \
  -tx http://localhost:8080/fhir \
  -output .github/terminology-tests/output \
  -mode flat \
  -filter good
