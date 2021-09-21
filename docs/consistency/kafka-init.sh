#!/bin/bash

check-broker-availability() {
  echo "check broker availability..."
  kafka-broker-api-versions.sh --bootstrap-server "$1:9092" \
    --command-config /opt/bitnami/kafka/config/kafka-init.conf > /dev/null 2> /dev/null
  [ $? -eq 1 ]
}

while check-broker-availability kafka-1; do sleep 1; done
while check-broker-availability kafka-2; do sleep 1; done
while check-broker-availability kafka-3; do sleep 1; done

kafka-topics.sh --zookeeper zookeeper-1:2181,zookeeper-2:2181,zookeeper-3:2181 --create --if-not-exists --topic tx \
  --partitions 1 --replication-factor 3 \
  --config message.timestamp.type=LogAppendTime --config retention.ms=-1
