#!/bin/bash -e

base="http://localhost:8080/fhir"
name="$1"

diagnostics=$(blazectl --server "$base" evaluate-measure --force-sync ".github/scripts/cql/$name.yml" 2> /dev/null | grep Diagnostics | cut -d: -f2 | xargs)

if [ "$diagnostics" = "Timeout of 10 millis eclipsed while evaluating." ]; then
  echo "✅ timeout happened"
else
  echo "🆘 no timeout"
  exit 1
fi
