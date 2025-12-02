#!/bin/bash

n=100000
c=8

import-once() {
  docker run --name blaze --rm -v blaze-data:/app/data \
    -e JAVA_TOOL_OPTIONS="-Xmx4g" \
    -e LOG_LEVEL=debug \
    -e DB_BLOCK_CACHE_SIZE=8192 \
    -e DB_MAX_BACKGROUND_JOBS=16 \
    -e DB_RESOURCE_INDEXER_THREADS=16 \
    -p 8080:8080 \
    -p 8081:8081 \
    -d samply/blaze:latest

  ../../.github/scripts/wait-for-url.sh  http://localhost:8080/health


  blazectl --server http://localhost:8080/fhir upload --no-progress output-100
  blazectl --server http://localhost:8080/fhir count-resources
  sleep 10

  blazectl --server http://localhost:8080/fhir upload --no-progress -c$c "output-$n" > "$n-c$c-$1.out"
  blazectl --server http://localhost:8080/fhir count-resources > "$n-count-resources-$1.out"

  docker stop blaze
  docker volume rm blaze-data
}

import-once 1
sleep 30
import-once 2
sleep 30
import-once 3
