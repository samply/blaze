#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

patient-valid-no-profile() {
cat <<END
{
  "resourceType": "Patient"
}
END
}

patient-invalid-with-profile() {
cat <<END
{
  "resourceType": "Patient",
  "meta": {
    "profile": "http://example.org/url-114730"
  }
}
END
}

patient-valid-with-profile() {
cat <<END
{
  "resourceType": "Patient",
  "active": true,
  "meta": {
    "profile": "http://example.org/url-114730"
  }
}
END
}

patient-valid-with-profile-as-bundle() {
cat <<END
{
  "resourceType": "Patient",
  "active": true,
  "meta": {
    "profile": "http://example.org/url-114730"
  }
}
END
}

bundle-valid-patient-with-profile() {
cat <<END
{ 
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    {
      "resource": $(patient-valid-with-profile),
      "request": {
        "method": "POST",
        "url": "/Patient"
      }
    }
  ]
}
END
}

structure-definition-patient() {
cat <<END
{
    "resourceType": "StructureDefinition",
    "name": "Patient-profile-1",
    "status": "active",
    "kind": "resource",
    "abstract": false,
    "url": "http://example.org/url-114730",
    "type": "Patient",
    "baseDefinition": "http://hl7.org/fhir/StructureDefinition/Patient",
    "derivation": "constraint",
    "differential": {
        "element": [
            {
                "id": "patient-active-rule",
                "path": "Patient.active",
                "mustSupport": true,
                "min": 1
            }
        ]
    }
}
END
}

BASE="http://localhost:8080/fhir"

echo "1: testing before Patient profile created"

echo "testing valid patient without profile"
RESULT_NO_PROFILE=$(patient-valid-no-profile | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE/Patient")
test "resource type" "$(echo "$RESULT_NO_PROFILE" | jq -r .resourceType)" "Patient"

echo "testing invalid patient with profile"
RESULT_INVALID_WITH_PROFILE=$(patient-invalid-with-profile | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE/Patient")
test "resource type" "$(echo "$RESULT_INVALID_WITH_PROFILE" | jq -r .resourceType)" "OperationOutcome"
test "issue diagnostics" "$(echo "$RESULT_INVALID_WITH_PROFILE" | jq -r .issue[0].diagnostics)" "Profile reference 'http://example.org/url-114730' has not been checked because it could not be found"

echo "testing valid patient with profile"
RESULT_VALID_WITH_PROFILE=$(patient-valid-with-profile | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE/Patient")
test "resource type" "$(echo "$RESULT_VALID_WITH_PROFILE" | jq -r .resourceType)" "OperationOutcome"
test "issue diagnostics" "$(echo "$RESULT_VALID_WITH_PROFILE" | jq -r .issue[0].diagnostics)" "Profile reference 'http://example.org/url-114730' has not been checked because it could not be found"

echo "testing valid patient with profile as bundle"
RESULT_BUNDLE_VALID_WITH_PROFILE=$(bundle-valid-patient-with-profile | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE")
test "resource type" "$(echo "$RESULT_VALID_WITH_PROFILE" | jq -r .resourceType)" "OperationOutcome"
test "issue diagnostics" "$(echo "$RESULT_VALID_WITH_PROFILE" | jq -r .issue[0].diagnostics)" "Profile reference 'http://example.org/url-114730' has not been checked because it could not be found"

echo "2: creating the patient profile"
RESULT_STRUCTURE_DEFINITION=$(structure-definition-patient | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE/StructureDefinition")
test "resource type" "$(echo "$RESULT_STRUCTURE_DEFINITION" | jq -r .resourceType)" "StructureDefinition"
STRUCTURE_DEFINITION_ID=$(echo "$RESULT_STRUCTURE_DEFINITION" | jq -r .id)

sleep 1
echo "3: testing after Patient profile created"
echo "testing valid patient without profile"
RESULT_NO_PROFILE=$(patient-valid-no-profile | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE/Patient")
test "resource type" "$(echo "$RESULT_NO_PROFILE" | jq -r .resourceType)" "Patient"

echo "testing invalid patient with profile"
RESULT_INVALID_WITH_PROFILE=$(patient-invalid-with-profile | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE/Patient")
test "resource type" "$(echo "$RESULT_INVALID_WITH_PROFILE" | jq -r .resourceType)" "OperationOutcome"
test "issue diagnostics" "$(echo "$RESULT_INVALID_WITH_PROFILE" | jq -r .issue[0].diagnostics)" "Patient.active: minimum required = 1, but only found 0 (from http://example.org/url-114730)"

echo "testing valid patient with profile"
RESULT_VALID_WITH_PROFILE=$(patient-valid-with-profile | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE/Patient")
test "resource type" "$(echo "$RESULT_VALID_WITH_PROFILE" | jq -r .resourceType)" "Patient"

echo "testing valid patient with profile as bundle"
RESULT_BUNDLE_VALID_WITH_PROFILE=$(bundle-valid-patient-with-profile | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE")
echo "$RESULT_BUNDLE_VALID_WITH_PROFILE"
test "resource type" "$(echo "$RESULT_BUNDLE_VALID_WITH_PROFILE" | jq -r .resourceType)" "Bundle"
test "response status" "$(echo "$RESULT_BUNDLE_VALID_WITH_PROFILE" | jq -r .entry[0].response.status)" "201"

sleep 1
echo "4: deleting Patient profile and testing invalid again"
RESULT_STRUCTURE_DEFINITION_DELETE=$(structure-definition-patient | curl -s -X DELETE -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE/StructureDefinition/$STRUCTURE_DEFINITION_ID")
test "delete response is empty" "$RESULT_STRUCTURE_DEFINITION_DELETE" ""

echo "testing invalid patient with profile"
RESULT_INVALID_WITH_PROFILE=$(patient-invalid-with-profile | curl -s -H 'Accept: application/fhir+json' -H "Content-Type: application/fhir+json" -d @- "$BASE/Patient")
test "resource type" "$(echo "$RESULT_INVALID_WITH_PROFILE" | jq -r .resourceType)" "OperationOutcome"
test "issue diagnostics" "$(echo "$RESULT_INVALID_WITH_PROFILE" | jq -r .issue[0].diagnostics)" "Profile reference 'http://example.org/url-114730' has not been checked because it could not be found"
