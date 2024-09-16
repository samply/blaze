#!/bin/bash -e

BASE="http://localhost:8080/fhir"
NAME="$1"

DIAGNOSTICS=$(blazectl --server "$BASE" evaluate-measure --force-sync ".github/scripts/cql/$NAME.yml" 2> /dev/null | grep Diagnostics | cut -d: -f2 | xargs)

if [ "$DIAGNOSTICS" = "Timeout of 10 millis eclipsed while evaluating." ]; then
  echo "✅ timeout happened"
else
  echo "🆘 no timeout"
  exit 1
fi
