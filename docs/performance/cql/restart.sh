#!/bin/bash -e

docker-compose -f ~/srv/data-1M/docker-compose.yml down
docker-compose -f ~/srv/data-1M/docker-compose.yml up -d
sleep 30
