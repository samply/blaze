#!/bin/bash -e

# This script tests that the _lastUpdated search works at least in a way that
# there are no patients updated after the current timestamp.
#
# The script assumes that Blaze contains at least some patients that were
# imported before this script runs.

base="http://localhost:8080/fhir"
now=$(date +%Y-%m-%dT%H:%M:%S)
patient_count=$(curl -sH 'Prefer: handling=strict' "$base/Patient?_lastUpdated=gt$now&_summary=count" | jq -r .total)

if [ "$patient_count" -eq 0 ]; then
  echo "✅ no patents are updated after $now"
else
  echo "🆘 $patient_count patents are updated after $now"
  exit 1
fi
