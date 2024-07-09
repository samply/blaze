#!/bin/bash -e

if [[ "$OSTYPE" == "darwin"* ]]; then
  TIME=gtime
else
  TIME=time
fi

calc-avg() {
  # Skip first two lines because they will not benefit from caching
  tail -n +3 "$1" |\
    awk '{sum += $1; sumsq += $1^2} END {printf("{\"avg\": %f, \"stddev\": %f}", sum/NR, sqrt(sumsq/NR - (sum/NR)^2))}'
}

restart() {
  docker compose -f "$1" restart
  sleep 30
}

calc-print-stats() {
  TIMES_FILE="$1"
  COUNT="$2"

  STATS="$(calc-avg "$TIMES_FILE")"
  AVG=$(echo "$STATS" | jq .avg)

  # the avg time per 1 million hits
  AVG_1M=$(echo "scale=2; $AVG * 10^6 / $COUNT" | bc)

  # shorten the count
  if (( $(echo "$COUNT > 1000000" | bc) )); then
    COUNT=$(echo "scale=2; $COUNT / 1000000" | bc)
    COUNT_FORMAT="%4.1f M"
  else
    COUNT=$(echo "scale=2; $COUNT / 1000" | bc)
    COUNT_FORMAT="%4.0f k"
  fi

  printf "| $COUNT_FORMAT | %8.2f | %6.3f | %6.2f |\n" "$COUNT" "$AVG" "$(echo "$STATS" | jq .stddev)" "$AVG_1M"
}

count-resources-raw() {
  BASE="$1"
  RESOURCE_TYPE="$2"
  SEARCH_PARAMS="$3"
  TIMES_FILE="$4"

  COUNT=$(curl -s "$BASE/$RESOURCE_TYPE?$SEARCH_PARAMS&_summary=count" | jq .total)

  # this are 7 tests of which 5 will be taken for the statistics
  for i in {0..6}; do
    curl -s "$BASE/$RESOURCE_TYPE?$SEARCH_PARAMS&_summary=count" -o /dev/null -w '%{time_starttransfer}\n' >> "$TIMES_FILE"
  done

  calc-print-stats "$TIMES_FILE" "$COUNT"
}

download-resources-raw() {
  BASE="$1"
  RESOURCE_TYPE="$2"
  SEARCH_PARAMS="$3"
  TIMES_FILE="$4"

  COUNT=$(curl -s "$BASE/$RESOURCE_TYPE?$SEARCH_PARAMS&_summary=count" | jq .total)

  # this are 5 tests of which 3 will be taken for the statistics
  for i in {0..4}; do
    $TIME -f "%e" -a -o "$TIMES_FILE" blazectl download --server "$BASE" "$RESOURCE_TYPE" -q "$SEARCH_PARAMS&_count=1000" >/dev/null 2>/dev/null
  done

  calc-print-stats "$TIMES_FILE" "$COUNT"
}
