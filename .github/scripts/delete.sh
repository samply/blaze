#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"
patient_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
headers=$(curl -sfXDELETE -D - "$base/Patient/$patient_id")

test_empty "content type header" "$(echo "$headers" | grep -i content-type | tr -d '\r')"

patient_history=$(curl -s "$base/Patient/$patient_id/_history")

total=$(echo "$patient_history" | jq .total)
if [ "$total" = "1" ]; then
  echo "✅ patient history has one entry"
else
  echo "🆘 patient history has $total entries"
  exit 1
fi

method=$(echo "$patient_history" | jq -r .entry[].request.method)
if [ "$method" = "DELETE" ]; then
  echo "✅ patient history entry has method DELETE"
else
  echo "🆘 patient history entry has method $method"
  exit 1
fi

status=$(echo "$patient_history" | jq -r .entry[].response.status)
if [ "$status" = "204" ]; then
  echo "✅ patient history entry has status 204"
else
  echo "🆘 patient history entry has status $status"
  exit 1
fi

patient_status=$(curl -is "$base/Patient/$patient_id" -o /dev/null -w '%{response_code}')
if [ "$patient_status" = "410" ]; then
  echo "✅ patient status is HTTP/1.1 410 Gone"
else
  echo "🆘 patient status is $patient_status"
  exit 1
fi

patient_outcome=$(curl -s "$base/Patient/$patient_id")

code=$(echo "$patient_outcome" | jq -r .issue[].code)
if [ "$code" = "deleted" ]; then
  echo "✅ patient outcome has code deleted"
else
  echo "🆘 patient outcome has code $code"
  exit 1
fi
