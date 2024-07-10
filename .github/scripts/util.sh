#!/bin/bash -e

test() {
  if [ "$2" = "$3" ]; then
    echo "OK 👍: the $1 is $3"
  else
    echo "Fail 😞: the $1 is $2, expected $3"
    exit 1
  fi
}

test-le() {
  if [ "$2" -le "$3" ]; then
    echo "OK 👍: the $1 of $2 is <= $3"
  else
    echo "Fail 😞: the $1 is $2, expected <= $3"
    exit 1
  fi
}

test_empty() {
  if [ -z "$2" ]; then
    echo "OK 👍: the $1 is empty"
  else
    echo "Fail 😞: the $1 is $2, should be empty"
    exit 1
  fi
}

create() {
  curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1"
}

update() {
  curl -XPUT -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- -o /dev/null "$1"
}

transact() {
  curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$1"
}

transact_return_representation() {
  curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -H "Prefer: return=representation" -d @- "$1"
}
