#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

# Returns the URL of the self link of the search result of a GET request to the
# URL $1.
self_link() {
  curl -sSfH 'Accept: application/fhir+json' "$1" | jq -r '.link[] | select(.relation == "self") | .url'
}

url="$base/Patient?name=Le%C3%B3n"
total=$(curl -sSfH 'Accept: application/fhir+json' "$url&_summary=count" | jq .total)

test "number of patients found" "$total" "1"
test "encoded search param value" "$(self_link "$url" | awk -F'[?&=]' '{print $3}')" "Le%C3%B3n"

url="$base/Condition?code=59621000,10509002"
total=$(curl -sSfH 'Accept: application/fhir+json' "$url&_summary=count" | jq .total)

test "number of conditions found" "$total" "93"
test "encoded search param value" "$(self_link "$url" | awk -F'[?&=]' '{print $3}')" "59621000%2C10509002"
