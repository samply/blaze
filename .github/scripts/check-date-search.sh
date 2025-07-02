#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TYPE=$1

download() {
  blazectl --server "$BASE" download "$TYPE" -q "date=$1" 2>/dev/null | wc -l | xargs
}

count() {
  curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$BASE/$TYPE?date=$1&_summary=count" | jq .total
}

size() {
  DOWNLOAD_SIZE=$(download $1)
  COUNT_SIZE=$(count $1)
  if [ "$DOWNLOAD_SIZE" = "$COUNT_SIZE" ]; then
    echo "$DOWNLOAD_SIZE"
  else
    echo "🆘 the download size is $DOWNLOAD_SIZE, expected $COUNT_SIZE"
    exit 1
  fi
}

for YEAR in {1990..2020}; do
  YEAR_SIZE=$(size "$YEAR")
  MONTH_SIZE=0

  for MONTH in {1..12}; do
    SIZE=$(size "$YEAR-$(printf "%02d" "$MONTH")")
    MONTH_SIZE=$((MONTH_SIZE + SIZE))
  done

  test_le "$YEAR months sum" "$MONTH_SIZE" "$YEAR_SIZE"
  test "[$YEAR-01-01, $YEAR-12-31] size" "$(size "sa$((YEAR - 1))-12-31&date=eb$((YEAR + 1))-01-01")" "$YEAR_SIZE"

done
