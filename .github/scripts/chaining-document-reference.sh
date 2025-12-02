#!/bin/bash -e

base="http://localhost:8080/fhir"

bundle() {
cat <<END
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "resource": {
        "resourceType": "DocumentReference",
        "id": "111430",
        "identifier": [
          {
            "system": "system-111302",
            "value": "value-111304"
          }
        ]
      },
      "request": {
        "method": "PUT",
        "url": "DocumentReference/111430"
      }
    },
    {
      "resource": {
        "resourceType": "DocumentReference",
        "id": "105551",
        "identifier": [
          {
            "system": "system-111302",
            "value": "value-111304"
          }
        ],
        "author": [
          {
            "reference": "Organization/105545"
          }
        ]
      },
      "request": {
        "method": "PUT",
        "url": "DocumentReference/105551"
      }
    },
    {
      "resource": {
        "resourceType": "DocumentReference",
        "id": "111917",
        "identifier": [
          {
            "system": "system-111302",
            "value": "value-111304"
          }
        ],
        "author": [
          {
            "reference": "Organization/111026"
          }
        ]
      },
      "request": {
        "method": "PUT",
        "url": "DocumentReference/111917"
      }
    },
    {
      "resource": {
        "resourceType": "DocumentReference",
        "id": "111020",
        "author": [
          {
            "reference": "Organization/111026"
          }
        ]
      },
      "request": {
        "method": "PUT",
        "url": "DocumentReference/111020"
      }
    },
    {
      "resource": {
        "resourceType": "DocumentReference",
        "id": "111206",
        "author": [
          {
            "reference": "Patient/111115"
          }
        ]
      },
      "request": {
        "method": "PUT",
        "url": "DocumentReference/111206"
      }
    },
    {
      "resource": {
        "resourceType": "Patient",
        "id": "111115",
        "identifier": [
          {
            "system": "system-105539",
            "value": "value-105542"
          }
        ]
      },
      "request": {
        "method": "PUT",
        "url": "Patient/111115"
      }
    },
    {
      "resource": {
        "resourceType": "Organization",
        "id": "105545",
        "identifier": [
          {
            "system": "system-105539",
            "value": "value-105542"
          }
        ]
      },
      "request": {
        "method": "PUT",
        "url": "Organization/105545"
      }
    },
    {
      "resource": {
        "resourceType": "Organization",
        "id": "111026",
        "identifier": [
          {
            "system": "system-105539",
            "value": "value-111043"
          }
        ]
      },
      "request": {
        "method": "PUT",
        "url": "Organization/111026"
      }
    }
  ]
}
END
}

curl -sfH "Content-Type: application/fhir+json" -d "$(bundle)" -o /dev/null "$base"

result="$(curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/DocumentReference?author:Organization.identifier=system-105539|value-105542&_summary=count" | jq -r '.total')"

if [ "$result" = "1" ]; then
  echo "✅ chaining works"
else
  echo "🆘 chaining doesn't work"
  exit 1
fi

result="$(curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/DocumentReference?identifier=system-111302|value-111304&author:Organization.identifier=system-105539|value-105542&_summary=count" | jq -r '.total')"

if [ "$result" = "1" ]; then
  echo "✅ chaining works"
else
  echo "🆘 chaining doesn't work"
  exit 1
fi

result="$(curl -sH 'Prefer: handling=strict' -H 'Accept: application/fhir+json' "$base/DocumentReference?author:Organization.identifier=system-105539|value-105542&identifier=system-111302|value-111304&_summary=count" | jq -r '.total')"

if [ "$result" = "1" ]; then
  echo "✅ chaining works"
else
  echo "🆘 chaining doesn't work"
  exit 1
fi
