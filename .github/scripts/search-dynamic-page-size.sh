#!/bin/bash -e

#
# The script conducts a fhir search and checks that less than requested matches
# are returned on the first page, which indicates the fhir server is dynamically
# reducing page-size to mitigate more than 10000 resources per page.
# * The first argument is a fhir search query which should yield multiple patient resources.
# * The second argument is the requested page size.
# * The third argument the actual number of matches that will be returned.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
query="${1//[[:space:]]/}"
page_size="$2"
match_count="$3"
result="$(curl -sSf "$base/Patient?$query&_count=$page_size&_revinclude=Observation:subject")"

patient_count="$(echo "$result" | jq -r '[ .entry[] | select(.search.mode == "match") | .resource] | length')"
# $page_size Patients would be too costly. Blaze returns only $match_count
test "number of patients in the bundle" "$patient_count" "$match_count"

self_link=$(echo "$result" | jq -r '.link[] | select(.relation == "self") | .url')
expected_self_link="$base/Patient?${query//,/%2C}&_revinclude=Observation%3Asubject&_count=$page_size"
if [ "$self_link" = "$expected_self_link" ]; then
  echo "✅ the self link includes the original page-size"
else
  echo "🆘 the self link '$self_link' does not include original page-size, expected '$expected_self_link'"
  exit 1
fi
