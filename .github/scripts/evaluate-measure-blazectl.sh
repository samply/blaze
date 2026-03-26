#!/bin/bash
set -euo pipefail

base="http://localhost:8080/fhir"
name="$1"
expected_count="$2"

if ! report=$(blazectl --server "$base" evaluate-measure ".github/scripts/cql/$name.yml"); then
  echo "Measure evaluation failed: $report"
  exit 1
fi

count=$(echo "$report" | jq '.group[0].population[0].count')

if [ "$count" = "$expected_count" ]; then
  echo "✅ count ($count) equals the expected count"
else
  echo "🆘 count ($count) != $expected_count"
  exit 1
fi
