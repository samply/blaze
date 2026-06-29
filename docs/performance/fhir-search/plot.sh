#!/bin/bash
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"
dir="$PWD"

# Generate the chart data files from the tables in ../fhir-search.md into a
# temporary directory. The data files are derived from the markdown and are not
# committed.
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
./gen-chart-data.sh "$tmp"

# Render every chart. gnuplot runs in the temp dir (where the *.txt data live);
# the resulting *.png are moved next to the gnuplot scripts.
for gnuplot_file in *-download-1M.gnuplot; do
  (cd "$tmp" && gnuplot "$dir/$gnuplot_file")
done
mv "$tmp"/*.png "$dir/"
