#!/bin/bash -e

#
# This script fetches the CapabilityStatement through a batch request.
#

BASE="http://localhost:8080/fhir"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "request": {
        "method": "GET",
        "url": "metadata"
      }
    }
  ]
}
END
}
RESULT=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" "$BASE")

RESOURCE_TYPE="$(echo "$RESULT" | jq -r .resourceType)"
if [ "$RESOURCE_TYPE" = "Bundle" ]; then
  echo "✅ the resource type is Bundle"
else
  echo "🆘 the resource type is $RESOURCE_TYPE, expected Bundle"
  exit 1
fi

BUNDLE_TYPE="$(echo "$RESULT" | jq -r .type)"
if [ "$BUNDLE_TYPE" = "batch-response" ]; then
  echo "✅ the bundle type is batch-response"
else
  echo "🆘 the bundle type is $BUNDLE_TYPE, expected batch-response"
  exit 1
fi

RESPONSE_STATUS="$(echo "$RESULT" | jq -r .entry[].response.status)"
if [ "$RESPONSE_STATUS" = "200" ]; then
  echo "✅ the response status is 200"
else
  echo "🆘 the response status is $RESPONSE_STATUS, expected 200"
  exit 1
fi

RESPONSE_RESOURCE_TYPE="$(echo "$RESULT" | jq -r .entry[].resource.resourceType)"
if [ "$RESPONSE_RESOURCE_TYPE" = "CapabilityStatement" ]; then
  echo "✅ resource type is CapabilityStatement"
else
  echo "🆘 resource type was $RESPONSE_RESOURCE_TYPE but should be CapabilityStatement"
  exit 1
fi
