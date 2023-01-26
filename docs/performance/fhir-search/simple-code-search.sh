#!/bin/bash

VOLUME="blaze-data-1000000-poc"
DIR="$HOME/fhir-data/output-1000000-poc"
HEAP_SIZE=30
BLOCK_CACHE_SIZE=10240
RESOURCE_CACHE_SIZE=30000000
RESOURCE_HANDLE_CACHE_SIZE=30000000

start-blaze() {
  echo "Starting Blaze..."
  docker run --name blaze --rm -v "$VOLUME:/app/data" \
      -e JAVA_TOOL_OPTIONS="-Xmx${HEAP_SIZE}g" \
      -e LOG_LEVEL=debug \
      -e DB_BLOCK_CACHE_SIZE=$BLOCK_CACHE_SIZE \
      -e DB_RESOURCE_CACHE_SIZE=$RESOURCE_CACHE_SIZE \
      -e DB_RESOURCE_HANDLE_CACHE_SIZE=$RESOURCE_HANDLE_CACHE_SIZE \
      -e DB_MAX_BACKGROUND_JOBS=8 \
      -e DB_RESOURCE_INDEXER_THREADS=16 \
      -p 8080:8080 \
      -p 8081:8081 \
      -d samply/blaze:0.19

  ../../.github/scripts/wait-for-url.sh  http://localhost:8080/health
  echo "Finished"
}

#blazectl --server http://localhost:8080/fhir upload --no-progress -c16 $DIR

stop-blaze() {
  echo "Stopping Blaze..."
  docker stop blaze
  echo "Finished"
}

for CODE in "17861-6" "39156-5" "29463-7"
do
  start-blaze
  echo "Counting Observations with code $CODE..."
  for i in {1..6}
  do
    /usr/bin/time -f "%e" -a -o "count-$CODE.times" curl -s "http://localhost:8080/fhir/Observation?code=http://loinc.org|$CODE&_summary=count" | jq .total
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "count-$CODE.times" | awk '{t+=$1;n++} END {printf("Avg time: %.3f\n", t/n)}'

  echo "Downloading Observations with code $CODE..."
  for i in {1..6}
  do
    /usr/bin/time -f "%e" -a -o "download-$CODE.times" blazectl download --server http://localhost:8080/fhir Observation -q "code=http://loinc.org|$CODE&_count=1000" -o "$CODE-$(date +%s).ndjson"
  done

  # Skip first line because it will not benefit from caching
  tail -n +2 "download-$CODE.times" | awk '{t+=$1;n++} END {printf("Avg time: %.3f\n", t/n)}'

  stop-blaze
done
