#!/bin/bash
set -euo pipefail

# Tests the _lastUpdated search parameter against the currently imported
# patients. All checks derive their expected values from the data itself, so the
# script works for any dataset, but it is most meaningful on a large import like
# synthea-1000 whose transaction instants span several minutes.
#
# It verifies that:
#  * the range boundaries derived from the data are exact (no patient is updated
#    before the earliest or after the latest _lastUpdated),
#  * sorting by _lastUpdated (ascending and descending) pages over every patient
#    exactly once.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

count() {
  curl -sfH 'Accept: application/fhir+json' -H 'Prefer: handling=strict' \
    "$base/Patient?$1&_summary=count" | jq -r .total
}

# Returns the _lastUpdated of the first patient when sorted by `$1` (either
# `_lastUpdated` for the earliest or `-_lastUpdated` for the latest).
boundary_last_updated() {
  curl -sfH 'Accept: application/fhir+json' -H 'Prefer: handling=strict' \
    "$base/Patient?_sort=$1&_count=1" | jq -r '.entry[0].resource.meta.lastUpdated'
}

# Returns the number of unique patient ids downloaded when paging over all
# patients sorted by `$1`.
unique_sorted_count() {
  local page_size
  page_size="$(shuf -i 250-1000 -n 1)"
  blazectl --server "$base" download Patient -q "_sort=$1&_count=$page_size" 2> /dev/null |
    jq -r .id | sort -u | wc -l | xargs
}

total="$(count "")"
test_not_equal "total number of patients" "$total" "0"
echo "ℹ️ found $total patients"

earliest="$(boundary_last_updated "_lastUpdated")"
latest="$(boundary_last_updated "-_lastUpdated")"
echo "ℹ️ _lastUpdated ranges from $earliest to $latest"

test "number of patients updated at or after the earliest _lastUpdated" \
  "$(count "_lastUpdated=ge$earliest")" "$total"
test "number of patients updated at or before the latest _lastUpdated" \
  "$(count "_lastUpdated=le$latest")" "$total"
test "number of patients updated after the latest _lastUpdated" \
  "$(count "_lastUpdated=gt$latest")" "0"
test "number of patients updated before the earliest _lastUpdated" \
  "$(count "_lastUpdated=lt$earliest")" "0"

test "number of unique patients sorted by ascending _lastUpdated" \
  "$(unique_sorted_count "_lastUpdated")" "$total"
test "number of unique patients sorted by descending _lastUpdated" \
  "$(unique_sorted_count "-_lastUpdated")" "$total"
