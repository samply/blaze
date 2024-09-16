#!/bin/bash -e

PROTO=$1
EXPECTED_SELF_LINK="$PROTO://blaze.de/fhir/Patient"
ACTUAL_SELF_LINK=$(curl -s -H 'X-Forwarded-Host:blaze.de' -H "X-Forwarded-Proto:$PROTO" http://localhost:8080/fhir/Patient | jq -r '.link[] | select(.relation == "self") | .url' | cut -d? -f1)

if [ "$EXPECTED_SELF_LINK" = "$ACTUAL_SELF_LINK" ]; then
  echo "✅"
else
  echo "🆘 expected '$EXPECTED_SELF_LINK' but was '$ACTUAL_SELF_LINK'"
  exit 1
fi
