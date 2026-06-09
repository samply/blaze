#!/bin/bash
set -euo pipefail

#
# This script navigates the paging links of an Observation search taken from the
# FHIR Bundle (Bundle.link). It follows the next link twice (to the third page)
# and afterwards follows the previous link of the third page, expecting to arrive
# at the second page again. Going to the third page ensures the previous link
# points to a real middle page instead of being equivalent to the first link.
#

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

# Returns the URL of the link with relation $2 in the Bundle $1.
link_url() {
  echo "$1" | jq -r ".link[] | select(.relation == \"$2\") | .url"
}

# Returns the id of the first entry resource in the Bundle $1.
first_entry_id() {
  echo "$1" | jq -r '.entry[0].resource.id'
}

# first page
first_page="$(curl -sfH 'Accept: application/fhir+json' "$base/Observation")"
first_page_id="$(first_entry_id "$first_page")"
next_link="$(link_url "$first_page" "next")"

test_non_empty "next link of the first page" "$next_link"

# follow the next link to the second page
second_page="$(curl -sfH 'Accept: application/fhir+json' "$next_link")"
second_page_id="$(first_entry_id "$second_page")"
next_link="$(link_url "$second_page" "next")"

test_not_equal "first entry id of the second page" "$second_page_id" "$first_page_id"
test_non_empty "next link of the second page" "$next_link"

# follow the next link to the third page
third_page="$(curl -sfH 'Accept: application/fhir+json' "$next_link")"
third_page_id="$(first_entry_id "$third_page")"
previous_link="$(link_url "$third_page" "previous")"

test_not_equal "first entry id of the third page" "$third_page_id" "$second_page_id"
test_non_empty "previous link of the third page" "$previous_link"

# follow the previous link back to the second page
previous_page="$(curl -sfH 'Accept: application/fhir+json' "$previous_link")"
test "first entry id of the previous page" "$(first_entry_id "$previous_page")" "$second_page_id"
