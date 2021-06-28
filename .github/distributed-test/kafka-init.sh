#!/bin/bash

check-broker-availability() {
  echo "check broker availability..."
  kafka-broker-api-versions.sh --bootstrap-server kafka:9092 \
    --command-config /opt/bitnami/kafka/config/kafka-init.conf > /dev/null 2> /dev/null
  [ $? -eq 1 ]
}

while check-broker-availability; do sleep 1; done

kafka-topics.sh --zookeeper zookeeper:2181 --create --if-not-exists --topic tx \
  --partitions 1 --replication-factor 1 \
  --config message.timestamp.type=LogAppendTime --config retention.ms=-1
