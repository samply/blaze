#!/bin/bash -e

base="http://localhost:8080/fhir"

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

status=$(curl -sH "Content-Type: application/fhir+json" \
  -d "$(bundle)" "$base" | jq -r '.entry[].response.status')

if [ "$status" = "201" ]; then
  echo "✅ first attempt created the Organization"
else
  echo "🆘 status was ${status} but should be 201"
  exit 1
fi

status=$(curl -sH "Content-Type: application/fhir+json" \
  -d "$(bundle)" "$base" | jq -r '.entry[].response.status')

if [ "$status" = "200" ]; then
  echo "✅ second attempt returned the already created Organization"
else
  echo "🆘 status was ${status} but should be 200"
  exit 1
fi
