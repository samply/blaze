#!/bin/bash
set -euo pipefail

base="http://localhost:8080/fhir"
name="$1"

output=$(blazectl --server "$base" evaluate-measure --force-sync ".github/scripts/cql/$name.yml" 2>&1 || true)

# grep may not match; keep the pipeline from aborting the script under `set -e`.
diagnostics=$(grep -m1 Diagnostics <<<"$output" | cut -d: -f2- | xargs || true)

if [ "$diagnostics" = "Timeout of 10 millis eclipsed while evaluating." ]; then
  echo "✅ timeout happened"
else
  echo "🆘 no timeout"
  exit 1
fi
