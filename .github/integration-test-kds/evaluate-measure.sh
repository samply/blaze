#!/bin/bash

base="http://localhost:8080/fhir"
name="$1"
expected_count="$2"
path=".github/integration-test-kds"

report=$(blazectl --server "$base" evaluate-measure "$path/$name.yml")

if [ $? -ne 0 ]; then
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

stratifier_data=$(echo "$report" | jq -rf "$path/$name.jq")
expected_stratifier_data=$(cat "$path/$name.csv")

if [ "$stratifier_data" = "$expected_stratifier_data" ]; then
  echo "✅ stratifier data equals the expected stratifier data"
else
  echo "🆘 stratifier data differs"
  echo "$stratifier_data"
  exit 1
fi
