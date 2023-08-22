#!/bin/bash -e

BASE="http://localhost:8080/fhir"

if [ "406" = "$(curl -s -o /dev/null -w '%{response_code}' -H 'Accept: text/plain' "$BASE")" ]; then
  echo "OK 👍: text/plain is a not acceptable media type"
else
  echo "Fail 😞"
  exit 1
fi
