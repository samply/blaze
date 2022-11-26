#!/bin/bash -e

BASE="http://localhost:8080/fhir"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "fullUrl": "Organization/foo",
      "resource": {
        "resourceType": "Organization",
        "identifier": [
          {
            "system": "https://example.com/fhir/NamingSystem/organization",
            "value": "190723"
          }
        ]
      },
      "request": {
        "method": "POST",
        "url": "Organization",
        "ifNoneExist": "identifier=https://example.com/fhir/NamingSystem/organization|190723"
      }
    }
  ]
}
END
}

STATUS=$(curl -sH "Content-Type: application/fhir+json" \
  -d "$(bundle)" "$BASE" | jq -r '.entry[].response.status')

if [ "$STATUS" = "201" ]; then
  echo "OK: first attempt created the Organization"
else
  echo "Fail: status was ${STATUS} but should be 201"
  exit 1
fi

STATUS=$(curl -sH "Content-Type: application/fhir+json" \
  -d "$(bundle)" "$BASE" | jq -r '.entry[].response.status')

if [ "$STATUS" = "200" ]; then
  echo "OK: second attempt returned the already created Organization"
else
  echo "Fail: status was ${STATUS} but should be 200"
  exit 1
fi
