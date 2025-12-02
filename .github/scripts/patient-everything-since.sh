#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

patient() {
cat <<END
{
  "resourceType": "Patient"
}
END
}

patient_id=$(curl -sSfH "Content-Type: application/fhir+json" \
  -d "$(patient)" "$base/Patient" | jq -r '.id')

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
    "reference": "Patient/$patient_id"
  }
}
END
}

# Create an observation (A)
curl -sSfH "Content-Type: application/fhir+json" \
  -d "$(observation "A")" "$base/Observation" >/dev/null

# Create another observation (B)
before_b_time="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
curl -sSfH "Content-Type: application/fhir+json" \
  -d "$(observation "B")" "$base/Observation" >/dev/null

# Fetch full $everything bundle
bundle=$(curl -sSf "$base/Patient/$patient_id/\$everything")
actual_size=$(echo "$bundle" | jq -r .total)

# There should be at least 3 resources in the bundle (Patient and Observation A+B)
test "number of all resources" "$actual_size" "3"

# Fetch $everything bundle since 'before creating observation B'
bundle=$(curl -sSf "$base/Patient/$patient_id/\$everything?_since=${before_b_time}")
actual_size=$(echo "$bundle" | jq -r .total)

# There should be only 2 resources in the bundle (Patient and Observation B)
test "number of resources since B" "$actual_size" "2"

query() {
  echo ".entry[] | select(.resource.identifier[]? | .system == \"https://github.com/samply/blaze\" and .value == \"$1\")"
}

if ! echo "$bundle" | jq -e "$(query "A")" >/dev/null; then
  echo "✅ Observation A is not included in the bundle"
else
  echo "🆘 Observation A is included in the bundle"
  exit 1
fi

if echo "$bundle" | jq -e "$(query "B")" >/dev/null; then
  echo "✅ Observation B is included in the bundle"
else
  echo "🆘 Observation B is not included in the bundle"
  exit 1
fi
