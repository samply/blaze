# Distributed

## Kafka

List all topics:

```bash
docker run -it --rm --network kafka_default bitnami/kafka:2-debian-10 kafka-topics.sh --zookeeper zookeeper:2181 --list
```

Create the tx topic:

```bash
docker run -it --rm --network kafka_default bitnami/kafka kafka-topics.sh --zookeeper zookeeper:2181 --create --topic tx --partitions 1 --replication-factor 1 --config message.timestamp.type=LogAppendTime --config retention.ms=-1
```

Describe the tx topic:

```bash
docker run -it --rm --network kafka_default bitnami/kafka kafka-topics.sh --zookeeper zookeeper:2181 --describe tx
```

## Cassandra

Cassandra can be used as resource storage. You have to create a keyspace and one table for Blaze. You can use the Cassandra Query Language Shell via Docker running the following command:

```bash
docker run -it --rm --network cassandra_default cassandra:3 cqlsh cassandra
```

### Keyspace

```
CREATE KEYSPACE blaze WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 3};
```

### Table

```
CREATE TABLE blaze.resources (hash text PRIMARY KEY, content blob);
```
