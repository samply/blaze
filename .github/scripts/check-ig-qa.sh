#!/bin/bash

# Checks that the QA report of the rendered Implementation Guide lists no errors
# and no broken links. The IG Publisher reports both without failing, so without
# this check they would go unnoticed in a passing build.
#
# Usage: check-ig-qa.sh [<output-dir>]

set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

output="${1:-modules/ig/output}"

errors="$(jq -r '.errs' "$output/qa.json")"
broken_links="$(jq -r '."broken-links"' "$output/qa.json")"

if [ "$errors" != "0" ] || [ "$broken_links" != "0" ]; then
  echo "🆘 errors of $output/qa.txt:"
  grep '^ERROR: ' "$output/qa.txt" || true
fi

test "number of QA errors" "$errors" "0"
test "number of broken links" "$broken_links" "0"
