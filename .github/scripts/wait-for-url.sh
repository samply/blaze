#!/bin/bash -e

URL=$1
START_EPOCH="$(date +"%s")"

eclipsed() {
  EPOCH="$(date +"%s")"
  echo $((EPOCH - START_EPOCH))
}

# wait at maximum 120 seconds
while [[ ($(eclipsed) -lt 120) && ("$(curl -s -o /dev/null -w '%{response_code}' "$URL")" != "200") ]]; do
  sleep 2
done
