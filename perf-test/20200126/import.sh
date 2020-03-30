#!/bin/sh

BASE_URL=$1
n=$2
start=${3:-0}
end=${4:-10}

mkdir -p /tmp/fhir-test-data

for ((i = $start; i < $end; i = $i + 1)); do
  rm -r /tmp/fhir-test-data
  mkdir /tmp/fhir-test-data
  echo "gen ${n} patients starting at $((i * n)) ..."
  bbmri-fhir-gen -s $((i * n)) -n $n --seed $(date +'%s') /tmp/fhir-test-data
  blazectl --server="${BASE_URL}" upload -c4 /tmp/fhir-test-data
done
