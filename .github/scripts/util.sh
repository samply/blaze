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
  curl -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1"
}

update() {
  curl -XPUT -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1"
}

transact() {
  curl -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1"
}

transact_return_representation() {
  curl -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -H "Prefer: return=representation" -d @- "$1"
}

# Executes the operation at the URL $1 with the Parameters resource read from
# stdin and returns the response body. Fails on HTTP errors. Additional
# arguments are passed to curl, e.g. `-o /dev/null -D -` to return the response
# headers instead.
execute_operation() {
  execute_operation_allow_error "$1" -f "${@:2}"
}

# Like execute_operation, but returns the body of HTTP error responses instead
# of failing. Use it to test error responses.
execute_operation_allow_error() {
  curl -sH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "${@:2}" "$1"
}

# Returns the body of a successful GET request to the URL $1 using strict
# search parameter handling.
search_strict() {
  curl -sfH 'Accept: application/fhir+json' -H 'Prefer: handling=strict' "$1"
}

# Returns the Link header (RFC 8288) of a GET request to the URL $1.
link_header() {
  curl -sfH 'Accept: application/fhir+json' -o /dev/null -D - "$1" | grep -i '^link:' | tr -d '\r'
}

# Downloads the IDs of all patients of the List with ID $2 from the server $1.
fetch_patient_ids() {
  blazectl --server "$1" download Patient -q "_list=$2&_elements=id&_count=200" 2>/dev/null | jq -r '.id'
}
