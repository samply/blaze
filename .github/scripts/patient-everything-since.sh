#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

BASE="http://localhost:8080/fhir"

patient() {
cat <<END
{
  "resourceType": "Patient"
}
END
}

PATIENT_ID=$(curl -sSfH "Content-Type: application/fhir+json" \
  -d "$(patient)" "$BASE/Patient" | jq -r '.id')

observation() {
cat <<END
{
  "resourceType": "Observation",
  "status": "final",
  "code": {
    "text": "Body temperature"
  },
  "identifier": [
    {
      "system": "https://github.com/samply/blaze",
      "value": "$1"
    }
  ],
  "subject": {
    "reference": "Patient/$PATIENT_ID"
  }
}
END
}

# Create an observation (A)
curl -sSfH "Content-Type: application/fhir+json" \
  -d "$(observation "A")" "$BASE/Observation" >/dev/null

# Create another observation (B)
BEFORE_B_TIME="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
curl -sSfH "Content-Type: application/fhir+json" \
  -d "$(observation "B")" "$BASE/Observation" >/dev/null

# Fetch full $everything bundle
BUNDLE=$(curl -sSf "$BASE/Patient/$PATIENT_ID/\$everything")
ACTUAL_SIZE=$(echo "$BUNDLE" | jq -r .total)

# There should be at least 3 resources in the bundle (Patient and Observation A+B)
test "number of all resources" "$ACTUAL_SIZE" "3"

# Fetch $everything bundle since 'before creating observation B'
BUNDLE=$(curl -sSf "$BASE/Patient/$PATIENT_ID/\$everything?_since=${BEFORE_B_TIME}")
ACTUAL_SIZE=$(echo "$BUNDLE" | jq -r .total)

# There should be only 2 resources in the bundle (Patient and Observation B)
test "number of resources since B" "$ACTUAL_SIZE" "2"

query() {
  echo ".entry[] | select(.resource.identifier[]? | .system == \"https://github.com/samply/blaze\" and .value == \"$1\")"
}

if ! echo "$BUNDLE" | jq -e "$(query "A")" >/dev/null; then
  echo "✅ Observation A is not included in the bundle"
else
  echo "🆘 Observation A is included in the bundle"
  exit 1
fi

if echo "$BUNDLE" | jq -e "$(query "B")" >/dev/null; then
  echo "✅ Observation B is included in the bundle"
else
  echo "🆘 Observation B is not included in the bundle"
  exit 1
fi
