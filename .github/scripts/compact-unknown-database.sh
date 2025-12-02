#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

start_epoch="$(date +"%s")"

eclipsed() {
  local epoch="$(date +"%s")"
  echo $((epoch - start_epoch))
}

base="http://localhost:8080/fhir"

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "database",
      "valueCode": "foo"
    },
    {
      "name": "column-family",
      "valueCode": "bar"
    }
  ]
}
END
}

headers="$(curl -sfH 'Accept: application/fhir+json' -H 'Content-Type: application/fhir+json' -d "$(parameters)" -o /dev/null -D - "$base/\$compact")"
status_url=$(echo "$headers" | grep -i content-location | tr -d '\r' | cut -d: -f2- | xargs)

# wait for response available
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$status_url")" != "200") ]]; do
  sleep 0.1
done

bundle=$(curl -sH 'Accept: application/fhir+json' "$status_url")

test "bundle type" "$(echo "$bundle" | jq -r '.type')" "batch-response"
test "response status" "$(echo "$bundle" | jq -r '.entry[0].response.status')" "400"
test "response severity" "$(echo "$bundle" | jq -r '.entry[0].response.outcome.issue[0].severity')" "error"
test "response code" "$(echo "$bundle" | jq -r '.entry[0].response.outcome.issue[0].code')" "invalid"
test "response diagnostics" "$(echo "$bundle" | jq -r '.entry[0].response.outcome.issue[0].diagnostics')" "Unknown database \`foo\`."
