#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
type=$1

download() {
  blazectl --server "$base" download "$type" -q "date=$1" 2>/dev/null | wc -l | xargs
}

count() {
  curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/$type?date=$1&_summary=count" | jq .total
}

size() {
  local download_size=$(download $1)
  local count_size=$(count $1)
  if [ "$download_size" = "$count_size" ]; then
    echo "$download_size"
  else
    echo "🆘 the download size is $download_size, expected $count_size"
    exit 1
  fi
}

for year in {1990..2020}; do
  year_size=$(size "$year")
  month_size=0

  for month in {1..12}; do
    size=$(size "$year-$(printf "%02d" "$month")")
    month_size=$((month_size + size))
  done

  test_le "$year months sum" "$month_size" "$year_size"
  test "[$year-01-01, $year-12-31] size" "$(size "sa$((year - 1))-12-31&date=eb$((year + 1))-01-01")" "$year_size"

done
