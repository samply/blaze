#!/bin/bash -e

BASE="http://localhost:8080/fhir"
CODE="29463-7"

blazectl evaluate-measure "observation-$CODE-value.yml" --server "$BASE" > "observation-$CODE-value-report.json"
