#!/bin/bash -e

SELF_LINK=$(curl -s -H 'X-Forwarded-Host:blaze.de' -H 'X-Forwarded-Proto:https' http://localhost:8080/fhir/Patient | jq -r .link[0].url | cut -d? -f1)

if [ "https://blaze.de/fhir/Patient" = "$SELF_LINK" ]; then
  echo "Success"
  exit 0
else
  echo "Fail"
  exit 1
fi
