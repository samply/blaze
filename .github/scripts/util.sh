#!/bin/bash
set -euo pipefail

test() {
  if [ "$2" = "$3" ]; then
    echo "✅ the $1 is $3"
  else
    echo "🆘 the $1 is $2, expected $3"
    exit 1
  fi
}

test_not_equal() {
  if [ "$2" != "$3" ]; then
    echo "✅ the $1 is not $3"
  else
    echo "🆘 the $1 is $2, expected not $3"
    exit 1
  fi
}

test_regex() {
  if [[ "$2" =~ $3 ]]; then
    echo "✅ the $1 matches $3"
  else
    echo "🆘 the $1 is $2, expected matching $3"
    exit 1
  fi
}

test_le() {
  if [ "$2" -le "$3" ]; then
    echo "✅ the $1 of $2 is <= $3"
  else
    echo "🆘 the $1 is $2, expected <= $3"
    exit 1
  fi
}

test_empty() {
  if [ -z "$2" ]; then
    echo "✅ the $1 is empty"
  else
    echo "🆘 the $1 is $2, should be empty"
    exit 1
  fi
}

test_non_empty() {
  if [ -n "$2" ]; then
    echo "✅ the $1 is non-empty"
  else
    echo "🆘 the $1 is $2, should be non-empty"
    exit 1
  fi
}

create() {
  curl -sSfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1"
}

update() {
  curl -XPUT -sSfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1"
}

transact() {
  curl -sSfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1"
}

transact_return_representation() {
  curl -sSfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -H "Prefer: return=representation" -d @- "$1"
}

# Returns the body of a successful GET request to the URL $1 using strict
# search parameter handling.
search_strict() {
  curl -sSfH 'Accept: application/fhir+json' -H 'Prefer: handling=strict' "$1"
}

# Returns the history of the resource with type $2 and id $3 from the server $1.
# The optional $4 holds query params like `_count=1`.
fetch_instance_history() {
  curl -sSfH 'Accept: application/fhir+json' "$1/$2/$3/_history${4:+?$4}"
}

# Returns the history of all resources with type $2 from the server $1. The
# optional $3 holds query params like `_count=1`.
fetch_type_history() {
  curl -sSfH 'Accept: application/fhir+json' "$1/$2/_history${3:+?$3}"
}

# Returns the history of all resources from the server $1. The optional $2
# holds query params like `_count=1`.
fetch_system_history() {
  curl -sSfH 'Accept: application/fhir+json' "$1/_history${2:+?$2}"
}

# Returns the Link header (RFC 8288) of a GET request to the URL $1.
link_header() {
  curl -sSfH 'Accept: application/fhir+json' -o /dev/null -D - "$1" | grep -i '^link:' | tr -d '\r'
}

# Downloads the IDs of all patients of the List with ID $2 from the server $1.
fetch_patient_ids() {
  blazectl --server "$1" download Patient -q "_list=$2&_elements=id&_count=200" 2>/dev/null | jq -r '.id'
}
