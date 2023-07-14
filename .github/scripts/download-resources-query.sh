#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
TYPE=$1
QUERY=$2
EXPECTED_SIZE=$3
FILE_NAME_PREFIX="$(uuidgen)"

count() {
  curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$BASE/$TYPE?$QUERY&_summary=count" | jq .total
}

test "count size" "$(count)" "$EXPECTED_SIZE"

blazectl --no-progress --server "$BASE" download "$TYPE" -q "$QUERY" -o "$FILE_NAME_PREFIX-get".ndjson

SIZE=$(wc -l "$FILE_NAME_PREFIX-get".ndjson | xargs | cut -d ' ' -f1)
if [ "$EXPECTED_SIZE" = "$SIZE" ]; then
  echo "OK 👍: download size matches for GET request"
else
  echo "Fail 😞: download size was ${SIZE} but should be ${EXPECTED_SIZE} for GET request"
  exit 1
fi

blazectl --server "$BASE" download "$TYPE" -p -q "$QUERY" -o "$FILE_NAME_PREFIX-post".ndjson

SIZE=$(wc -l "$FILE_NAME_PREFIX-post".ndjson | xargs | cut -d ' ' -f1)
if [ "$EXPECTED_SIZE" = "$SIZE" ]; then
  echo "OK 👍: download size matches for POST request"
else
  echo "Fail 😞: download size was ${SIZE} but should be ${EXPECTED_SIZE} for POST request"
  exit 1
fi

if [ "$(diff "$FILE_NAME_PREFIX-get.ndjson" "$FILE_NAME_PREFIX-post.ndjson")" = "" ]; then
  echo "OK 👍: both downloads are identical"
else
  echo "Fail 😞: the GET and the POST download differ"
  exit 1
fi
