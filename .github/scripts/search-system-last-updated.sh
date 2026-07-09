#!/bin/bash
set -euo pipefail

#
# This script verifies that the system-wide search with _lastUpdated counts all
# resources for a range covering every update and no resources for a range
# after the last update. All checks derive their expected values from the data
# itself, so the script works for any dataset.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

count() {
  search_strict "$base?$1_summary=count" | jq -r .total
}

total="$(count "")"
test_not_equal "total number of resources" "$total" "0"
echo "ℹ️ found $total resources"

test "number of resources updated before the year 3000" \
  "$(count "_lastUpdated=lt3000-01-01&")" "$total"
test "number of resources updated after the year 3000" \
  "$(count "_lastUpdated=gt3000-01-01&")" "0"
