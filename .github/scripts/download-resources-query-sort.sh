#!/bin/bash -e

base="http://localhost:8080/fhir"
type=$1
query="${2//[[:space:]]/}"
sort=$3
expected_size=$4
file_name_prefix="$(uuidgen)"

blazectl --server "$base" download "$type" -q "_sort=$sort&$query&_count=$(shuf -i 50-500 -n 1)" -o "$file_name_prefix-get.ndjson"

size=$(wc -l "$file_name_prefix-get.ndjson" | xargs | cut -d ' ' -f1)
if [ "$expected_size" = "$size" ]; then
  echo "✅ download size matches for GET request"
else
  echo "🆘 download size was ${size} but should be ${expected_size} for GET request"
  rm "$file_name_prefix-get.ndjson"
  exit 1
fi

blazectl --server "$base" download "$type" -p -q "_sort=$sort&$query" -o "$file_name_prefix-post.ndjson"

size=$(wc -l "$file_name_prefix-post.ndjson" | xargs | cut -d ' ' -f1)
if [ "$expected_size" = "$size" ]; then
  echo "✅ download size matches for POST request"
else
  echo "🆘 download size was ${size} but should be ${expected_size} for POST request"
  rm "$file_name_prefix-get.ndjson"
  rm "$file_name_prefix-post.ndjson"
  exit 1
fi

if diff -q "$file_name_prefix-get.ndjson" "$file_name_prefix-post.ndjson" >/dev/null; then
  echo "✅ both downloads are identical"
  rm "$file_name_prefix-post.ndjson"
else
  echo "🆘 the GET and the POST download differ"
  rm "$file_name_prefix-get.ndjson"
  rm "$file_name_prefix-post.ndjson"
  exit 1
fi

if [[ "$sort" == "_id" ]]; then

  ids=$(cat "$file_name_prefix-get.ndjson" | jq -r '.id')
  ids_sort=$(echo "$ids" | sort)
  if [ "$ids" = "$ids_sort" ]; then
    echo "✅ resources are sorted by id"
  else
    echo "🆘 resources are not sorted by id"
    exit 1
  fi

elif [[ "$sort" == "_lastUpdated" || "$sort" == "-_lastUpdated" ]]; then

  # test sorting, ignoring the milliseconds because Blaze strips them in the index
  last_updated=$(cat "$file_name_prefix-get.ndjson" | jq -r '.meta.lastUpdated' | cut -d'.' -f1 | cut -d'Z' -f1 | uniq)
  rm "$file_name_prefix-get.ndjson"

  if [[ "$sort" == -* ]]; then
    last_updated_sort=$(echo "$last_updated" | sort -r)
  else
    last_updated_sort=$(echo "$last_updated" | sort)
  fi
  if [ "$last_updated" = "$last_updated_sort" ]; then
    echo "✅ resources are sorted by lastUpdated"
  else
    echo "🆘 resources are not sorted by lastUpdated"
    echo "$last_updated"
    exit 1
  fi
fi
