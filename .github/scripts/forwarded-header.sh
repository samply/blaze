#!/usr/bin/env -S bash -e

SELF_LINK=$(curl -s -H 'Forwarded:host=blaze.de;proto=https' http://localhost:8080/fhir/Patient | jq -r .link[0].url | cut -d? -f1)

if [ "https://blaze.de/fhir/Patient" = $SELF_LINK ]; then
  echo "Success"
  exit 0
else
  echo "Fail"
  exit 1
fi
