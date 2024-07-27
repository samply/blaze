#!/bin/bash -e

BASE="http://blaze-test-host:8080/fhir"

medication() {
cat <<END
{
    "resourceType": "Medication",
    "id": "$CODE",
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

CODES=$(blazectl --server "$BASE" download Medication -q "_count=1000" | jq -r '.code.coding[].code' | sort -u)

for CODE in $CODES; do
  curl -XPUT -H "Content-Type: application/fhir+json" -d "$(medication "$CODE")" "$BASE/Medication/$CODE"
done

## Update all MedicationAdministrations to a reference to the Medication resource

for RESOURCE in $(blazectl --server "$BASE" download MedicationAdministration -q "_count=1000" | jq -c 'del(.meta) | .medicationReference = {reference: ("Medication/" + .medicationCodeableConcept.coding[].code)} | del(.medicationCodeableConcept)'); do
  curl -XPUT -H "Content-Type: application/fhir+json" -d "$RESOURCE" "$BASE/MedicationAdministration/$(echo "$RESOURCE" | jq -r '.id')"
done

### Delete all old Medications

for ID in $(blazectl --server "$BASE" download Medication -q "_count=1000" | jq -r '.id' | grep -vE '^[0-9]+$'); do
  curl -XDELETE "$BASE/Medication/$ID"
done
