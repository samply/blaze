#!/bin/bash -e

# This script creates a large binary resource (8 MiB) and verifies that its binary content
# * can be read correctly via direct binary upload, and that
# * it's properly base64-encoded.

base="http://localhost:8080/fhir"

# Create temporary files for original and downloaded data
temp_original=$(mktemp)
temp_download=$(mktemp)
temp_json=$(mktemp)

# Ensure cleanup of temporary files
trap 'rm -f "$temp_original" "$temp_download" "$temp_json"' EXIT

echo "Testing direct binary upload and download..."

# Generate 8 MiB of random binary data
dd if=/dev/urandom bs=8388608 count=1 2>/dev/null > "$temp_original"

# Create Binary resource via direct binary upload
id=$(curl -sH 'Content-Type: application/octet-stream' --data-binary "@$temp_original" "$base/Binary" | jq -r '.id')

echo "Created Binary resource via direct binary upload with id: $id"

# Download as JSON format to verify base64 encoding
curl -sfH 'Accept: application/fhir+json' "$base/Binary/$id" > "$temp_json"

# Extract the base64 content and decode it
jq -r '.data' "$temp_json" | base64 -d > "$temp_download"

# Compare files directly
if [ -n "$id" ] && cmp -s "$temp_original" "$temp_download"; then
    echo "✅ Direct Binary: Successfully verified 8 MiB binary content integrity and base64 encoding"
else
    echo "🆘 Direct Binary: Content verification failed"
    echo "Server response (JSON):"
    echo "Original size   : $(wc -c < "$temp_original") bytes"
    echo "Downloaded size : $(wc -c < "$temp_download") bytes"
    # Show first few bytes of both files in hex for debugging
    echo "First 32 bytes of original:"
    hexdump -C "$temp_original" | head -n 2
    echo "First 32 bytes of downloaded:"
    hexdump -C "$temp_download" | head -n 2
    exit 1
fi
