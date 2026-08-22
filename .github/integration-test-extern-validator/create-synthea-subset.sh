#!/bin/bash
set -euo pipefail

# Creates a subset of the Synthea test data in the `synthea-subset` directory.
# Validating every resource of the full Synthea data set against the external
# validator would be too slow, so only one of the 121 patient bundles is loaded,
# together with the two master data bundles it references: the hospital and the
# practitioner information bundle. Those two master data bundles make up most of
# the roughly 840 resources of the subset.

script_dir="$(dirname "$(readlink -f "$0")")"
synthea_dir="$script_dir/../test-data/synthea"
target_dir="synthea-subset"

files=(
  "0-hospitalInformation1625911868739.json.bz2"
  "0-practitionerInformation1625911868739.json.bz2"
  "8a4c9c04-1524-9f1c-d65b-9b17e4520fef.json.bz2"
)

mkdir -p "$target_dir"
for file in "${files[@]}"; do
  cp "$synthea_dir/$file" "$target_dir/"
done

echo "ℹ️ created Synthea subset with ${#files[@]} bundles in $target_dir"
