#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

echo "checking index.html using the Accept header..."
HEADERS=$(curl -s -H 'Accept: text/html' -o /dev/null -D - "$BASE")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "Content-Type header" "$CONTENT_TYPE_HEADER" "text/html"

echo "checking index.html using the _format query param..."
HEADERS=$(curl -s -o /dev/null -D - "$BASE?_format=html")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "Content-Type header" "$CONTENT_TYPE_HEADER" "text/html"

echo "checking version.json..."
HEADERS=$(curl -s -H 'Accept: text/html' -o /dev/null -D - "$BASE/__frontend/version.json")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "Content-Type header" "$CONTENT_TYPE_HEADER" "application/json"

echo "checking system-search using the Accept header..."
HEADERS=$(curl -s -H 'Accept: application/fhir+json' -o /dev/null -D - "$BASE")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "Content-Type header" "$CONTENT_TYPE_HEADER" "application/fhir+json;charset=utf-8"

echo "checking system-search using the _format query param..."
HEADERS=$(curl -s -o /dev/null -D - "$BASE?_format=json")
CONTENT_TYPE_HEADER=$(echo "$HEADERS" | grep -i 'Content-Type' | tr -d '\r' | cut -d' ' -f2)

test "Content-Type header" "$CONTENT_TYPE_HEADER" "application/fhir+json;charset=utf-8"
