#!/bin/bash -e

BASE="http://localhost:8080/fhir"
TYPE=$1
QUERY=$2
SORT=$3
EXPECTED_SIZE=$4
FILE_NAME_PREFIX="$(uuidgen)"

blazectl --server "$BASE" download "$TYPE" -q "_sort=$SORT&$QUERY" -o "$FILE_NAME_PREFIX-get".ndjson

SIZE=$(wc -l "$FILE_NAME_PREFIX-get".ndjson | xargs | cut -d ' ' -f1)
if [ "$EXPECTED_SIZE" = "$SIZE" ]; then
  echo "Success: download size matches for GET request"
else
  echo "Fail: download size was ${SIZE} but should be ${EXPECTED_SIZE} for GET request"
  exit 1
fi

blazectl --server "$BASE" download "$TYPE" -p -q "_sort=$SORT&$QUERY" -o "$FILE_NAME_PREFIX-post".ndjson

SIZE=$(wc -l "$FILE_NAME_PREFIX-post".ndjson | xargs | cut -d ' ' -f1)
if [ "$EXPECTED_SIZE" = "$SIZE" ]; then
  echo "Success: download size matches for POST request"
else
  echo "Fail: download size was ${SIZE} but should be ${EXPECTED_SIZE} for POST request"
  exit 1
fi

if [ "$(diff "$FILE_NAME_PREFIX-get.ndjson" "$FILE_NAME_PREFIX-post.ndjson")" = "" ]; then
  echo "Success: both downloads are identical"
else
  echo "Fail: the GET and the POST download differ"
  exit 1
fi

# test sorting, ignoring the milliseconds because Blaze strips them in the index
LAST_UPDATED=$(cat "$FILE_NAME_PREFIX-get.ndjson" | jq -r '.meta.lastUpdated' | cut -d'.' -f1 | uniq)
if [[ "$SORT" == -* ]]; then
  LAST_UPDATED_SORT=$(echo "$LAST_UPDATED" | sort -r)
else
  LAST_UPDATED_SORT=$(echo "$LAST_UPDATED" | sort)
fi
if [ "$LAST_UPDATED" = "$LAST_UPDATED_SORT" ]; then
  echo "Success: resources are sorted"
else
  echo "Fail: resources are not sorted"
  exit 1
fi
