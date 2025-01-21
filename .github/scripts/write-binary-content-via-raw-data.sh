#!/bin/bash -e

# This script creates a large binary resource (8 MiB) and verifies that its binary content
# * can be read correctly via direct binary upload, and that
# * it's properly base64-encoded.

BASE="http://localhost:8080/fhir"

# Create temporary files for original and downloaded data
TEMP_ORIGINAL=$(mktemp)
TEMP_DOWNLOAD=$(mktemp)
TEMP_JSON=$(mktemp)

# Ensure cleanup of temporary files
trap 'rm -f "$TEMP_ORIGINAL" "$TEMP_DOWNLOAD" "$TEMP_JSON"' EXIT

echo "Testing direct binary upload and download..."

# Generate 8 MiB of random binary data
dd if=/dev/urandom bs=8388608 count=1 2>/dev/null > "$TEMP_ORIGINAL"

# Create Binary resource via direct binary upload
ID=$(curl -sH 'Content-Type: application/octet-stream' --data-binary "@$TEMP_ORIGINAL" "$BASE/Binary" | jq -r '.id')

echo "Created Binary resource via direct binary upload with ID: $ID"

# Download as JSON format to verify base64 encoding
curl -sfH 'Accept: application/fhir+json' "$BASE/Binary/$ID" > "$TEMP_JSON"

# Extract the base64 content and decode it
jq -r '.data' "$TEMP_JSON" | base64 -d > "$TEMP_DOWNLOAD"

# Compare files directly
if [ -n "$ID" ] && cmp -s "$TEMP_ORIGINAL" "$TEMP_DOWNLOAD"; then
    echo "✅ Direct Binary: Successfully verified 8 MiB binary content integrity and base64 encoding"
else
    echo "🆘 Direct Binary: Content verification failed"
    echo "Server response (JSON):"
    echo "Original size   : $(wc -c < "$TEMP_ORIGINAL") bytes"
    echo "Downloaded size : $(wc -c < "$TEMP_DOWNLOAD") bytes"
    # Show first few bytes of both files in hex for debugging
    echo "First 32 bytes of original:"
    hexdump -C "$TEMP_ORIGINAL" | head -n 2
    echo "First 32 bytes of downloaded:"
    hexdump -C "$TEMP_DOWNLOAD" | head -n 2
    exit 1
fi
