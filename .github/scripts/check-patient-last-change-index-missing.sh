#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
if curl -sfH 'Accept: application/json' "$base/__admin/dbs/index/column-families" | jq -r '.[].name' | grep -q "patient-last-change-index"; then
  echo "🆘 the patient-last-change-index is present, expected missing"
  exit 1
else
  echo "✅ the patient-last-change-index is missing"
fi
