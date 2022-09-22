#!/bin/bash -e

#
# This script creates a patient and tries to retrieve it through a batch request.
#

BASE="http://localhost:8080/fhir"
PATIENT_ID=$(curl -sH "Content-Type: application/fhir+json" \
  -d '{"resourceType": "Patient"}' \
  "$BASE/Patient" | jq -r .id)

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "batch",
  "entry": [
    {
      "request": {
        "method": "GET",
        "url": "Patient/$PATIENT_ID"
      }
    }
  ]
}
END
}
RESULT=$(curl -sH "Content-Type: application/fhir+json" -d "$(bundle)" "$BASE")

RESOURCE_TYPE="$(echo "$RESULT" | jq -r .resourceType)"
if [ "$RESOURCE_TYPE" = "Bundle" ]; then
  echo "OK: the resource type is Bundle"
else
  echo "Fail: the resource type is $RESOURCE_TYPE, expected Bundle"
  exit 1
fi

BUNDLE_TYPE="$(echo "$RESULT" | jq -r .type)"
if [ "$BUNDLE_TYPE" = "batch-response" ]; then
  echo "OK: the bundle type is batch-response"
else
  echo "Fail: the bundle type is $BUNDLE_TYPE, expected batch-response"
  exit 1
fi

RESPONSE_STATUS="$(echo "$RESULT" | jq -r .entry[].response.status)"
if [ "$RESPONSE_STATUS" = "200" ]; then
  echo "OK: the response status is 200"
else
  echo "Fail: the response status is $RESPONSE_STATUS, expected 200"
  exit 1
fi

RESPONSE_PATIENT_ID="$(echo "$RESULT" | jq -r .entry[].resource.id)"
if [ "$RESPONSE_PATIENT_ID" = "$PATIENT_ID" ]; then
  echo "OK: patient id's match"
else
  echo "Fail: response patient id was $RESPONSE_PATIENT_ID but should be $PATIENT_ID"
  exit 1
fi
