#!/bin/bash -e

BASE="http://localhost:8080/fhir"

for CODE in "17861-6" "39156-5" "29463-7"
do
  echo "Counting Observations with code $CODE..."
  for i in {1..6}
  do
    /usr/bin/time -f "%e" -a -o "count-$CODE.times" curl -s "$BASE/Observation?code=http://loinc.org|$CODE&_summary=count" | jq .total
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "count-$CODE.times" | awk '{t+=$1;n++} END {printf("Avg time: %.3f\n", t/n)}'

  echo "Downloading Observations with code $CODE..."
  for i in {1..6}
  do
    /usr/bin/time -f "%e" -a -o "download-$CODE.times" blazectl download --server "$BASE" Observation -q "code=http://loinc.org|$CODE&_count=1000" > /dev/null 2> /dev/null
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "download-$CODE.times" | awk '{t+=$1;n++} END {printf("Avg time: %.3f\n", t/n)}'

  echo "Downloading Observations with code $CODE and _elements=subject..."
  for i in {1..6}
  do
    /usr/bin/time -f "%e" -a -o "download-subject-$CODE.times" blazectl download --server "$BASE" Observation -q "code=http://loinc.org|$CODE&_elements=subject&_count=1000" > /dev/null 2> /dev/null
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "download-subject-$CODE.times" | awk '{t+=$1;n++} END {printf("Avg time: %.3f\n", t/n)}'
done
