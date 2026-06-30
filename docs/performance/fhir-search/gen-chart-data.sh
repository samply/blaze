#!/bin/bash
# Generates the gnuplot data files (*.txt) for the FHIR Search download charts
# directly from the tables in ../fhir-search.md. This keeps the markdown the
# single source of truth: edit the tables there, then run plot.sh to regenerate
# the data files and the charts.
#
# The data files are derived artifacts and are not committed; plot.sh generates
# them into a temporary directory. Usage: gen-chart-data.sh [output-dir]
# (the output directory defaults to the current directory).
#
# Each data file row has the form
#
#   | <System> | <Series> | <Resources/s in k> |
#
# ordered system-major (so a chart with N series can slice it with `every N`).
# The value is the numeric part of the "Res/s" column in thousands (the " k"
# suffix is stripped).
set -euo pipefail

outdir="${1:-.}"
mkdir -p "$outdir"
outdir="$(cd "$outdir" && pwd)"

cd "$(dirname "$(readlink -f "$0")")"
md="../fhir-search.md"

# extract-rows <section-title>
# Prints the body rows of the (non-_elements) "Downloading Resources" table of
# the given "## <section-title>" section.
extract_rows() {
  awk -v section="## $1" '
    $0 == section {in_section=1; next}
    in_section && /^## / {exit}
    in_section && $0 == "### Downloading Resources" {in_dl=1; next}
    in_dl && /^### / {exit}
    in_dl && /^\|/ && !/---/ {print}
  ' "$md"
}

# gen <out> <section> <system-col> <series-col> <value-col> [series-filter]
# Column indices are 1-based into the data cells of the markdown table. A
# series-col of 0 means the chart has no series. The optional series-filter is a
# comma-separated allow-list of series values (used to drop rows from a chart).
gen() {
  local out="$outdir/$1" section="$2" sys_col="$3" series_col="$4" val_col="$5" filter="${6:-}"
  extract_rows "$section" | awk -F'|' \
    -v sc="$sys_col" -v rc="$series_col" -v vc="$val_col" -v filter="$filter" '
    BEGIN { if (filter != "") { split(filter, f, ","); for (i in f) allow[f[i]] = 1 } }
    {
      delete a; n = 0
      for (i = 2; i < NF; i++) { v = $i; gsub(/^ +| +$/, "", v); a[++n] = v }
      if (a[1] == "System") next   # header row
      if (a[2] != "1M") next       # only the 1M dataset is charted
      series = (rc > 0) ? a[rc] : ""
      if (filter != "" && !(series in allow)) next
      value = a[vc]; gsub(/[^0-9.]/, "", value)
      printf "| %s | %s | %s |\n", a[sc], series, value
    }' > "$out"
  echo "wrote $out ($(wc -l < "$out" | xargs) rows)"
}

gen simple-code-search-download-1M.txt        "Simple Code Search"                  1 3 7
gen multiple-codes-search-download-1M.txt      "Multiple Codes Search"              1 3 7
gen multiple-codes-search-vs-download-1M.txt   "Multiple Codes Search – ValueSet"   1 3 7
gen multiple-search-param-search-download-1M.txt "Multiple Search Parameter Search" 1 3 8
gen code-value-search-download-1M.txt          "Code and Value Search"             1 4 8
gen code-patient-search-download-1M.txt        "Code and Patient Search"           1 3 7
gen multiple-codes-patient-search-download-1M.txt "Multiple Codes and Patient Search" 1 3 7 "10,100"
gen code-date-patient-search-download-1M.txt    "Code, Date, and Patient Search"    1 3 7
gen simple-date-search-download-1M.txt          "Simple Date Search"                1 3 7
gen token-forward-chaining-search-download-1M.txt "Token and Forward Chaining Search" 1 0 6
gen patient-date-search-download-1M.txt         "Patient Date Search"               1 3 7
