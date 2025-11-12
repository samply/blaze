#!/bin/bash -e

if [[ "$OSTYPE" == "darwin"* ]]; then
  TIME=gtime
else
  TIME=time
fi

calc-avg() {
  # Skip first line because it is used for warmup
  tail -n +2 "$1" |\
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

  # resources per second
  RES_S=$(echo "scale=2; $COUNT / $AVG" | bc)

  # shorten the count
  if (( $(echo "$COUNT > 1000000" | bc) )); then
    COUNT=$(echo "scale=2; $COUNT / 1000000" | bc)
    COUNT_FORMAT="%4.1f M"
  elif (( $(echo "$COUNT > 1000" | bc) )); then
    COUNT=$(echo "scale=2; $COUNT / 1000" | bc)
    COUNT_FORMAT="%4.0f k"
  else
    COUNT_FORMAT="%6.0f"
  fi

  # shorten the resources per second
  if (( $(echo "$RES_S > 1000000" | bc) )); then
    RES_S=$(echo "scale=2; $RES_S / 1000000" | bc)
    RES_S_FORMAT="%4.1f M"
  elif (( $(echo "$RES_S > 1000" | bc) )); then
    RES_S=$(echo "scale=2; $RES_S / 1000" | bc)
    RES_S_FORMAT="%4.1f k"
  else
    RES_S_FORMAT="%6.0f"
  fi

  printf "| $COUNT_FORMAT | %8.2f | %6.3f | $RES_S_FORMAT |\n" "$COUNT" "$AVG" "$(echo "$STATS" | jq .stddev)" "$RES_S"
}

count-resources-raw() {
  BASE="$1"
  RESOURCE_TYPE="$2"
  SEARCH_PARAMS="$3"
  TIMES_FILE="$4"

  COUNT=$(curl -s "$BASE/$RESOURCE_TYPE?$SEARCH_PARAMS&_summary=count" | jq .total)

  # this are 4 tests of which 3 will be taken for the statistics
  for i in {0..3}; do
    curl -s "$BASE/$RESOURCE_TYPE?$SEARCH_PARAMS&_summary=count" -o /dev/null -w '%{time_total}\n' >> "$TIMES_FILE"
  done

  calc-print-stats "$TIMES_FILE" "$COUNT"
}

count-resources-raw-post() {
  BASE="$1"
  RESOURCE_TYPE="$2"
  SEARCH_PARAMS="$3"
  TIMES_FILE="$4"
  URL="$BASE/$RESOURCE_TYPE/_search?_summary=count"

  COUNT=$(curl -s -d @<(echo -n "$SEARCH_PARAMS") "$URL" | jq .total)

  # this are 4 tests of which 3 will be taken for the statistics
  for i in {0..3}; do
    curl -s -d @<(echo -n "$SEARCH_PARAMS") "$URL" -o /dev/null -w '%{time_total}\n' >> "$TIMES_FILE"
  done

  calc-print-stats "$TIMES_FILE" "$COUNT"
}

download-resources-raw() {
  BASE="$1"
  RESOURCE_TYPE="$2"
  SEARCH_PARAMS="$3"
  TIMES_FILE="$4"

  COUNT=$(curl -s "$BASE/$RESOURCE_TYPE?$SEARCH_PARAMS&_summary=count" | jq .total)

  # this are 4 tests of which 3 will be taken for the statistics
  for i in {0..3}; do
    DOWNLOAD_COUNT=$($TIME -f "%e" -a -o "$TIMES_FILE" blazectl download --server "$BASE" "$RESOURCE_TYPE" -q "$SEARCH_PARAMS&_count=1000" 2>/dev/null | wc -l | xargs)

    if [ "$COUNT" != "$DOWNLOAD_COUNT" ]; then
      echo "🆘 the number of downloaded resources ($DOWNLOAD_COUNT) doesn't match the count of $COUNT"
      exit 1
    fi
  done

  calc-print-stats "$TIMES_FILE" "$COUNT"
}

download-resources-raw-post() {
  BASE="$1"
  RESOURCE_TYPE="$2"
  SEARCH_PARAMS="$3"
  TIMES_FILE="$4"

  COUNT=$(curl -s -d @<(echo -n "$SEARCH_PARAMS") "$BASE/$RESOURCE_TYPE/_search?_summary=count" | jq .total)

  # this are 4 tests of which 3 will be taken for the statistics
  for i in {0..3}; do
    DOWNLOAD_COUNT=$($TIME -f "%e" -a -o "$TIMES_FILE" blazectl download --server "$BASE" "$RESOURCE_TYPE" -p -q @<(echo -n "$SEARCH_PARAMS&_count=1000") 2>/dev/null | wc -l | xargs)

    if [ "$COUNT" != "$DOWNLOAD_COUNT" ]; then
      echo "🆘 the number of downloaded resources ($DOWNLOAD_COUNT) doesn't match the count of $COUNT"
      exit 1
    fi
  done

  calc-print-stats "$TIMES_FILE" "$COUNT"
}

add_system() {
    local system="$1"
    local codes="$2"
    echo "$codes" | sed "s#^#${system}|#" | paste -sd ',' -
}
