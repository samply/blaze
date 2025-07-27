#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TYPE=$1
QUERY="${2//[[:space:]]/}"
EXPECTED_SIZE=$3
FILE_NAME_PREFIX="$(uuidgen)"

summary_count() {
  curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$BASE/$TYPE?$QUERY&_summary=count" | jq .total
}

total_count() {
  curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$BASE/$TYPE?$QUERY&_total=accurate" | jq .total
}

test "_summary=count count" "$(summary_count)" "$EXPECTED_SIZE"
test "_total=accurate count" "$(total_count)" "$EXPECTED_SIZE"

blazectl --server "$BASE" download "$TYPE" -q "$QUERY&_count=$(shuf -i 50-500 -n 1)" -o "$FILE_NAME_PREFIX-get.ndjson" 2> /dev/null

SIZE=$(wc -l "$FILE_NAME_PREFIX-get.ndjson" | xargs | cut -d ' ' -f1)
if [ "$EXPECTED_SIZE" = "$SIZE" ]; then
  echo "✅ download size matches for GET request"
else
  echo "🆘 download size was ${SIZE} but should be ${EXPECTED_SIZE} for GET request"
  rm "$FILE_NAME_PREFIX-get.ndjson"
  exit 1
fi

NUM_UNIQUE_IDS="$(jq -r .id "$FILE_NAME_PREFIX-get.ndjson" | sort -u | wc -l | xargs)"
if [ "$EXPECTED_SIZE" = "$NUM_UNIQUE_IDS" ]; then
  echo "✅ all resource IDs are unique"
else
  echo "🆘 there are at least some non-unique resource IDs"
  rm "$FILE_NAME_PREFIX-get.ndjson"
  exit 1
fi

blazectl --server "$BASE" download "$TYPE" -p -q "$QUERY" -o "$FILE_NAME_PREFIX-post.ndjson" 2> /dev/null

SIZE=$(wc -l "$FILE_NAME_PREFIX-post.ndjson" | xargs | cut -d ' ' -f1)
if [ "$EXPECTED_SIZE" = "$SIZE" ]; then
  echo "✅ download size matches for POST request"
else
  echo "🆘 download size was ${SIZE} but should be ${EXPECTED_SIZE} for POST request"
  rm "$FILE_NAME_PREFIX-get.ndjson"
  rm "$FILE_NAME_PREFIX-post.ndjson"
  exit 1
fi

if diff -q "$FILE_NAME_PREFIX-get.ndjson" "$FILE_NAME_PREFIX-post.ndjson" >/dev/null; then
  echo "✅ both downloads are identical"
  rm "$FILE_NAME_PREFIX-get.ndjson"
  rm "$FILE_NAME_PREFIX-post.ndjson"
else
  echo "🆘 the GET and the POST download differ"
  rm "$FILE_NAME_PREFIX-get.ndjson"
  rm "$FILE_NAME_PREFIX-post.ndjson"
  exit 1
fi
