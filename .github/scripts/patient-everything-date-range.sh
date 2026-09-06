#!/bin/bash
set -euo pipefail

#
# This script invokes Patient $everything with a date range, once with the in
# parameters in the query string and once with the in parameters in a
# Parameters resource in the body of a POST request. Both have to return the
# same result.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_identifier="X79746011X"
patient_id=$(curl -sfH 'Accept: application/fhir+json' "$base/Patient?identifier=$patient_identifier" | jq -r '.entry[0].resource.id')

# Checks the Bundle $2 of the $everything request $1.
check_bundle() {
  local name="$1"
  local bundle="$2"
  local actual_size
  local ids
  actual_size=$(echo "$bundle" | jq -r .total)
  ids="$(echo "$bundle" | jq -r '.entry[].resource.id')"

  test "size of the $name bundle" "$actual_size" "1997"

  test "no duplicates in the $name bundle" "$(echo "$ids" | sort -u | wc -l | xargs)" "$(echo "$ids" | wc -l | xargs)"

  test "type counts of the $name bundle" "$(echo "$bundle" | jq -r '.entry | group_by(.resource.resourceType)[] | [.[0].resource.resourceType, length] | @csv')" "$(cat "$script_dir/patient-everything/$patient_identifier-type-counts-2013-2014.csv")"
}

check_bundle "GET" "$(curl -sfH 'Accept: application/fhir+json' "$base/Patient/$patient_id/\$everything?start=2013&end=2014")"

check_bundle "POST" "$(curl -sfH 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' -d @- "$base/Patient/$patient_id/\$everything" <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "start",
      "valueDate": "2013"
    },
    {
      "name": "end",
      "valueDate": "2014"
    }
  ]
}
END
)"
