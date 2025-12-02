#!/bin/bash -e

base="http://localhost:8080/fhir"
code="29463-7"

blazectl --server "$base" evaluate-measure "observation-$code-value.yml" > "observation-$code-value-report.json"

jq -rf "observation-value.jq" "observation-$code-value-report.json" > "observation-$code-value-report.csv"
