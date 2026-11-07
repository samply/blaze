#!/bin/bash
set -euo pipefail

base="http://localhost:8080/fhir"

patient_ids="$(blazectl --server "$base" download Patient -q '_elements=id&_count=1000' 2>/dev/null | jq -r '.id')"

# A Provenance isn't purged, because it can target several patients. The one
# Synthea creates for each patient targets only that patient, so it's deleted
# before the purge, which would fail otherwise.
echo "$patient_ids" | \
  xargs -P 4 -I {} curl -sS -H 'Accept: application/fhir+json' -X DELETE "$base/Provenance?patient={}" -o /dev/null

echo "$patient_ids" | \
  xargs -P 4 -I {} curl -sS -H 'Accept: application/fhir+json' -X POST "$base/Patient/{}/\$purge" -o /dev/null
