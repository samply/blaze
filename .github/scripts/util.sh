#!/bin/bash -e

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
  curl -XPUT -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- -o /dev/null "$1"
}

transact() {
  curl -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1"
}

transact_return_representation() {
  curl -sfH 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -H "Prefer: return=representation" -d @- "$1"
}
