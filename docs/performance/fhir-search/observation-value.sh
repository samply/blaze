#!/bin/bash -e

BASE="http://localhost:8080/fhir"
CODE="29463-7"

blazectl --server "$BASE" evaluate-measure "observation-$CODE-value.yml" > "observation-$CODE-value-report.json"

jq -rf "observation-value.jq" "observation-$CODE-value-report.json" > "observation-$CODE-value-report.csv"
