#!/bin/bash -e

url=$1
start_epoch="$(date +"%s")"

eclipsed() {
  local epoch="$(date +"%s")"
  echo $((epoch - start_epoch))
}

# wait at maximum 120 seconds
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$url")" != "200") ]]; do
  sleep 2
done
