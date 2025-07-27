#!/bin/bash -e

BASE="http://localhost:8080/fhir"
TYPE=$1
QUERY="${2//[[:space:]]/}"
SORT=$3
EXPECTED_SIZE=$4
FILE_NAME_PREFIX="$(uuidgen)"

blazectl --server "$BASE" download "$TYPE" -q "_sort=$SORT&$QUERY&_count=$(shuf -i 50-500 -n 1)" -o "$FILE_NAME_PREFIX-get.ndjson"

SIZE=$(wc -l "$FILE_NAME_PREFIX-get.ndjson" | xargs | cut -d ' ' -f1)
if [ "$EXPECTED_SIZE" = "$SIZE" ]; then
  echo "✅ download size matches for GET request"
else
  echo "🆘 download size was ${SIZE} but should be ${EXPECTED_SIZE} for GET request"
  rm "$FILE_NAME_PREFIX-get.ndjson"
  exit 1
fi

blazectl --server "$BASE" download "$TYPE" -p -q "_sort=$SORT&$QUERY" -o "$FILE_NAME_PREFIX-post.ndjson"

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
  rm "$FILE_NAME_PREFIX-post.ndjson"
else
  echo "🆘 the GET and the POST download differ"
  rm "$FILE_NAME_PREFIX-get.ndjson"
  rm "$FILE_NAME_PREFIX-post.ndjson"
  exit 1
fi

if [[ "$SORT" == "_id" ]]; then

  IDS=$(cat "$FILE_NAME_PREFIX-get.ndjson" | jq -r '.id')
  IDS_SORT=$(echo "$IDS" | sort)
  if [ "$IDS" = "$IDS_SORT" ]; then
    echo "✅ resources are sorted by id"
  else
    echo "🆘 resources are not sorted by id"
    exit 1
  fi

elif [[ "$SORT" == "_lastUpdated" || "$SORT" == "-_lastUpdated" ]]; then

  # test sorting, ignoring the milliseconds because Blaze strips them in the index
  LAST_UPDATED=$(cat "$FILE_NAME_PREFIX-get.ndjson" | jq -r '.meta.lastUpdated' | cut -d'.' -f1 | cut -d'Z' -f1 | uniq)
  rm "$FILE_NAME_PREFIX-get.ndjson"

  if [[ "$SORT" == -* ]]; then
    LAST_UPDATED_SORT=$(echo "$LAST_UPDATED" | sort -r)
  else
    LAST_UPDATED_SORT=$(echo "$LAST_UPDATED" | sort)
  fi
  if [ "$LAST_UPDATED" = "$LAST_UPDATED_SORT" ]; then
    echo "✅ resources are sorted by lastUpdated"
  else
    echo "🆘 resources are not sorted by lastUpdated"
    echo "$LAST_UPDATED"
    exit 1
  fi
fi
