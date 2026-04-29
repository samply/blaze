#!/bin/bash

java -jar validator_cli.jar txTests -test-version 1.8.0 \
  -tx http://localhost:8080/fhir \
  -trace-log validator-debug.log -txLog tx.log \
  -output .github/terminology-tests/output \
  -mode flat \
  -filter simple-expand

echo "Debug Log:"
cat validator-debug.log

echo "TX Log:"
cat tx.log
