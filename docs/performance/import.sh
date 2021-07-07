#!/bin/bash

N=100000

import-once() {
  docker run --name blaze --rm -v blaze-data:/app/data \
    --network blaze \
    -e JAVA_TOOL_OPTIONS="-Xmx4g" \
    -e LOG_LEVEL=debug \
    -e DB_BLOCK_CACHE_SIZE=1024 \
    -e DB_MAX_BACKGROUND_JOBS=16 \
    -p 8080:8080 \
    -p 8081:8081 \
    -d samply/blaze:rocksdb-tuning

  sleep 40

  # Check that Blaze is running
  SOFTWARE_NAME=$(curl -s http://localhost:8080/fhir/metadata | jq -r .software.name)
  if [ "Blaze" != "$SOFTWARE_NAME" ]; then
    echo "Fail"
    exit 1
  fi

  blazectl --server http://localhost:8080/fhir upload output-100
  blazectl --server http://localhost:8080/fhir count-resources
  sleep 60

  blazectl --server http://localhost:8080/fhir upload -c8 output-$N > $N-c8-$1.out
  blazectl --server http://localhost:8080/fhir count-resources > $N-count-resources-$1.out

  docker stop blaze
  docker volume rm blaze-data
}

import-once 1
sleep 30
import-once 2
sleep 30
import-once 3
