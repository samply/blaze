#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
type=$1
query="${2//[[:space:]]/}"
expected_size=$3
file_name_prefix="$(uuidgen)"

summary_count() {
  curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/$type?$query&_summary=count" | jq .total
}

total_count() {
  curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/$type?$query&_total=accurate" | jq .total
}

test "_summary=count count" "$(summary_count)" "$expected_size"
test "_total=accurate count" "$(total_count)" "$expected_size"

page_size="$(shuf -i 50-500 -n 1)"
echo "ℹ️ use a page size of $page_size"

blazectl --server "$base" download "$type" -q "$query&_count=$page_size" -o "$file_name_prefix-get.ndjson" 2> /dev/null

if [ "$(jq -r .id "$file_name_prefix-get.ndjson")" = "$(jq -r .id "$file_name_prefix-get.ndjson" | sort)" ]; then
  echo "ℹ️ resource IDs are sorted"
else
  echo "ℹ️ resource IDs are not sorted (not necessarily a failure)"
fi

size=$(wc -l "$file_name_prefix-get.ndjson" | xargs | cut -d ' ' -f1)
if [ "$expected_size" = "$size" ]; then
  echo "✅ download size matches for GET request"
else
  echo "🆘 download size was ${size} but should be ${expected_size} for GET request"
  rm "$file_name_prefix-get.ndjson"
  exit 1
fi

num_unique_ids="$(jq -r .id "$file_name_prefix-get.ndjson" | sort -u | wc -l | xargs)"
if [ "$expected_size" = "$num_unique_ids" ]; then
  echo "✅ all resource IDs are unique"
else
  echo "🆘 there are at least some non-unique resource IDs"
  rm "$file_name_prefix-get.ndjson"
  exit 1
fi

blazectl --server "$base" download "$type" -p -q "$query" -o "$file_name_prefix-post.ndjson" 2> /dev/null

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
  rm "$file_name_prefix-get.ndjson"
  rm "$file_name_prefix-post.ndjson"
else
  echo "🆘 the GET and the POST download differ"
  rm "$file_name_prefix-get.ndjson"
  rm "$file_name_prefix-post.ndjson"
  exit 1
fi
