#!/bin/bash -e

# This script tests that the _lastUpdated search works at least in a way that
# there are no patients updated after the current timestamp.
#
# The script assumes that Blaze contains at least some patients that were
# imported before this script runs.

BASE="http://localhost:8080/fhir"
NOW=$(date +%Y-%m-%dT%H:%M:%S)
PATIENT_COUNT=$(curl -sH 'Prefer: handling=strict' "$BASE/Patient?_lastUpdated=gt$NOW&_summary=count" | jq -r .total)

if [ "$PATIENT_COUNT" -eq 0 ]; then
  echo "✅ no patents are updated after $NOW"
else
  echo "🆘 $PATIENT_COUNT patents are updated after $NOW"
  exit 1
fi
