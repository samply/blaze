#!/bin/bash -e

base="http://blaze-test-host:8080/fhir"

medication() {
cat <<END
{
    "resourceType": "Medication",
    "id": "$code",
    "status": "active",
    "code": {
        "coding": [
            {
                "system": "http://www.nlm.nih.gov/research/umls/rxnorm",
                "code": "$1"
            }
        ]
    }
}
END
}

## Create Medication resources with the RxNorm code as id

codes=$(blazectl --server "$base" download Medication -q "_count=1000" | jq -r '.code.coding[].code' | sort -u)

for code in $codes; do
  curl -XPUT -H "Content-Type: application/fhir+json" -d "$(medication "$code")" "$base/Medication/$code"
done

## Update all MedicationAdministrations to a reference to the Medication resource

for resource in $(blazectl --server "$base" download MedicationAdministration -q "_count=1000" | jq -c 'del(.meta) | .medicationReference = {reference: ("Medication/" + .medicationCodeableConcept.coding[].code)} | del(.medicationCodeableConcept)'); do
  curl -XPUT -H "Content-Type: application/fhir+json" -d "$resource" "$base/MedicationAdministration/$(echo "$resource" | jq -r '.id')"
done

### Delete all old Medications

for id in $(blazectl --server "$base" download Medication -q "_count=1000" | jq -r '.id' | grep -vE '^[0-9]+$'); do
  curl -XDELETE "$base/Medication/$id"
done
