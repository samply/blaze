#!/bin/bash

java -jar validator_cli.jar -txTests -version current \
  -tx http://localhost:8080/fhir \
  -output .github/terminology-tests/output \
  -mode flat \
  -filter simple-expand
