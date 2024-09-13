#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"
HEADERS=$(curl -s -H 'Prefer: respond-async' -H 'Accept: application/fhir+json' -o /dev/null -D - "$BASE/Observation?date=lt2100&_summary=count")
STATUS_URL=$(echo "$HEADERS" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

STATUS_CODE=$(curl -s -XDELETE -o /dev/null -w '%{response_code}' "$STATUS_URL")
test "cancel status code" "$STATUS_CODE" "202"

test "status code after cancel" "$(curl -s -o /dev/null -w '%{response_code}' "$STATUS_URL")" "404"
RESPONSE="$(curl -s "$STATUS_URL")"
test "after cancel resource type" "$(echo "$RESPONSE" | jq -r .resourceType)" "OperationOutcome"
test "after cancel issue code" "$(echo "$RESPONSE" | jq -r '.issue[0].code')" "not-found"

DIAGNOSTICS="$(curl -s -H 'Accept: application/fhir+json' "$STATUS_URL" | jq -r '.issue[0].diagnostics')"
if [[ "$DIAGNOSTICS" =~ The\ asynchronous\ request\ with\ id\ \`[A-Z0-9]+\`\ is\ cancelled. ]]; then
    echo "✅ the diagnostics message is right"
else
    echo "🆘 the diagnostics message is $DIAGNOSTICS, expected /The asynchronous request with id \`[A-Z0-9]\+\` is cancelled./"
fi
