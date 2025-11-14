#!/bin/bash -e

proto=$1
expected_self_link="$proto://blaze.de/fhir/Patient"
actual_self_link=$(curl -s -H 'X-Forwarded-Host:blaze.de' -H "X-Forwarded-Proto:$proto" http://localhost:8080/fhir/Patient | jq -r '.link[] | select(.relation == "self") | .url' | cut -d? -f1)

if [ "$expected_self_link" = "$actual_self_link" ]; then
  echo "✅"
else
  echo "🆘 expected '$expected_self_link' but was '$actual_self_link'"
  exit 1
fi
