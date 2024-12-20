#!/bin/bash -e

# This script creates a binary resource with invalid data and verifies that,
# when trying to read its binary content, an error is generated.

BASE="http://localhost:8080/fhir"

# 10 KiB of random data, base64 encoded
ORIGINAL_DATA="$(openssl rand -base64 10240 | tr -d '\n')"


# Higher-order function to apply a transformation based on a condition
apply_generic_transformation() {
    local data="$1"
    local transform_function="$2"

    local result=""

    # Iterate over the data and apply the transform function
    for i in $(seq 0 ${#data}-1); do
        result+=$(eval "$transform_function \"${data:$i:1}\" $i")
    done

    echo "$result"
}

remove_parts_transform() {
    local char="$1"
    local index="$2"

    # Randomly decide whether to remove this character (skip it)
    if (( RANDOM % 10 == 0 )); then
        echo ""  # Skip (remove this character)
    else
        echo "$char"  # Keep the character
    fi
}

insert_non_base64_transform() {
    local char="$1"
    local index="$2"

    # Every 5th character, insert a random non-base64 character
    if (( index % 5 == 0 )); then
        echo "$((RANDOM % 10))"  # Insert a non-base64 digit
    else
        echo "$char"  # Keep the character
    fi
}

# Apply Remove Parts of the Data
FAULTY_DATA=$(apply_generic_transformation "$ORIGINAL_DATA" "remove_parts_transform")
# echo "Faulty Data (Removed Parts): $FAULTY_DATA"

# Apply Insert Non-Base64 Characters
FAULTY_DATA=$(apply_generic_transformation "$FAULTY_DATA" "insert_non_base64_transform")
# echo "Faulty Data (also with Non-Base64 Characters): $FAULTY_DATA"

DATA=$FAULTY_DATA


binary() {
cat <<END
{
  "resourceType": "Binary",
  "contentType": "application/pdf",
  "data": "$DATA"
}
END
}

# Create a Binary resource that contains that faulty data, and get its ID (via JSON)
ID_VIA_JSON=$(curl -s -H 'Content-Type: application/fhir+json' -d "$(binary)" "$BASE/Binary" | jq -r '.id')

echo "Created Binary resource that contains the Random Data"
echo "  - via JSON, with ID: $ID_VIA_JSON"


# Retrieve the Binary resource, and Base64 encode it so it can be safely handled by Bash (JSON)
BASE64_ENCODED_BINARY_RESOURCE_VIA_JSON=$(curl -s -H 'Accept: application/pdf' "$BASE/Binary/$ID_VIA_JSON" | base64 | tr -d '\n')


echo "Binary data retrieved. Verifying content... (JSON version)"

if [ "$DATA" = "$BASE64_ENCODED_BINARY_RESOURCE_VIA_JSON" ]; then
    echo "✅ Base64 encoding of both the Original Data and the Retrieved Resource Data match (JSON)"
else
    echo "🆘 Base64 encoding of both the Original Data and the Retrieved Resource Data are different (JSON)"
    exit 1
fi
