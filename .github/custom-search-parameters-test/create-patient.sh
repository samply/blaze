#!/bin/bash -e

base="http://localhost:8080/fhir"

patient() {
cat <<END
{
  "resourceType": "Patient",
  "maritalStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus",
        "code": "M"
      }
    ]
  }
}
END
}

curl -fsH 'Content-Type: application/fhir+json' -d "$(patient)" -o /dev/null "$base/Patient"
