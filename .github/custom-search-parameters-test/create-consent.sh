#!/bin/bash
set -euo pipefail

# creates a Consent with id $1 having a nested provision with a code and the
# period from $2 to $3

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/../scripts/util.sh"

base="http://localhost:8080/fhir"
id="$1"
start="$2"
end="$3"

consent() {
cat <<END
{
  "resourceType": "Consent",
  "id": "$id",
  "status": "active",
  "scope": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/consentscope",
        "code": "research"
      }
    ]
  },
  "category": [
    {
      "coding": [
        {
          "system": "http://loinc.org",
          "code": "57016-8"
        }
      ]
    }
  ],
  "provision": {
    "type": "deny",
    "provision": [
      {
        "type": "permit",
        "period": {
          "start": "$start",
          "end": "$end"
        },
        "code": [
          {
            "coding": [
              {
                "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                "code": "2.16.840.1.113883.3.1937.777.24.5.3.8"
              }
            ]
          }
        ]
      }
    ]
  }
}
END
}

consent | update "$base/Consent/$id" > /dev/null
