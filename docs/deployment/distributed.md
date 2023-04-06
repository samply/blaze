# Distributed

Blaze can be deployed in three different storage variants: in-memory, standalone and distributed. In this section, the deployment of the distributed storage variant is described.

The distributed storage variant should be used for production setups that require high- availability and/or horizontal read scalability. 

As described in the [Architecture](../architecture.md) section, Blaze roughly consists of three components, the Blaze Node itself, a Transaction Log and a Resource Store. With the standalone storage variant, both the Transaction Log and the Resource Store are embedded inside the Blaze process. However using the distributed storage variant, the Transaction Log and the Resource Store are extern to Blaze and allow more than one Blaze Node to run. Being able to run more than one Blaze Node enables high availability and horizontal read scalability.

## Docker Compose Example

The `docs/deployment/distributed` directory contains a Docker Compose file with an example system consisting of all components needed in a distributed setup. 

Please be aware that we **don't recommend** to run Blaze on a single server using our example setup. You most likely will run the individual components in a [Kubernetes][11] Cluster or on separate VM's. The main purpose of this setup is to explain the components and their configuration in detail. Your production setup will and has to differ from this example in order to accomplish your high-availability, security and scalability goals.

You will need about 32 GB of RAM to be able to run the example smoothly. You can start it by going into the `docs/deployment/distributed` directory and run:

```sh
docker-compose up
```

Currently, Blaze uses [Kafka][1] for the Transaction Log and [Cassandra][4] for the Resource Store, so the example uses the same. A walk-through of the components follows:

### Zookeeper

As Kafka needs Zookeeper in order to coordinate its nodes, you have to run one.

The section of the Docker Compose file is shown below. The Zookeeper deployment is minimal and not production ready. It's only show because it's necessary. Please read the Kafka and Zookeeper documentation in order to provide a production ready setup.

```yaml
zookeeper:
  image: "docker.io/bitnami/zookeeper:3"
  volumes:
  - "zookeeper-data:/bitnami"
  environment:
    ALLOW_ANONYMOUS_LOGIN: "yes"
```

**Note**: In the future, Kafka will be able to run [without Zookeeper][3].

### Kafka

As explained already above and especially in the [Architecture](../architecture.md) section, Blaze uses one single Kafka topic to coordinate its transactions between multiple nodes.

The Kafka section of the Docker Compose file, shown below, contains a setup of a single Kafka node. In a production ready setup you most likely will run three Kafka nodes in order to replicate the messages and so making Kafka high available itself.

```yaml
kafka:
  image: "docker.io/bitnami/kafka:2"
  hostname: "kafka"
  environment:
    KAFKA_CFG_ZOOKEEPER_CONNECT: "zookeeper:2181"
    ALLOW_PLAINTEXT_LISTENER: "yes"
    KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: "CLIENT:SSL"
    KAFKA_CFG_LISTENERS: "CLIENT://:9092"
    KAFKA_CFG_ADVERTISED_LISTENERS: "CLIENT://kafka:9092"
    KAFKA_INTER_BROKER_LISTENER_NAME: "CLIENT"
    KAFKA_CERTIFICATE_PASSWORD: "password"
    KAFKA_CFG_TLS_TYPE: "JKS"
    # It's important to create the tx topic ourself, because it needs to use
    # LogAppendTime timestamps
    KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE: "false"
  volumes:
  - './kafka.keystore.jks:/opt/bitnami/kafka/config/certs/kafka.keystore.jks:ro'
  - './kafka.truststore.jks:/opt/bitnami/kafka/config/certs/kafka.truststore.jks:ro'
  - "kafka-data:/bitnami"
  depends_on:
  - zookeeper
```

Regarding authentication and transport, we choose to use Kafka's SSL transport and client certificates in order to demonstrate a production ready connection from Blaze to Kafka in our example. The SSL configuration was taken from the [Security section][5] of the [Bitnami Docker Image README][6]. Another good source of documentation can be found in the [Security section][7] of the [Kafka documentation][8]. The Java Truststore and Keystore were created with the [kafka-generate-ssl.sh][9] script provided by [Confluent][10]. Again, you will most likely have other ways to generate your keys in production.

Kafka itself will start with no topics created and we disabled automatic topic creation with purpose, by setting `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE` to `false`, because Blaze needs a topic with specific properties.

### Kafka Topic Creator

The purpose of the Kafka Topic Creator creator component shown below is solely to create the transaction topic Blaze needs. In your production environment, you will most likely create the topic yourself of how we explain in a minute.

```yaml
kafka-topic-creator:
  image: "docker.io/bitnami/kafka:2"
  command: /opt/bitnami/kafka/bin/kafka-init.sh
  volumes:
  - "./kafka-init.sh:/opt/bitnami/kafka/bin/kafka-init.sh:ro"
  - "./kafka-init.conf:/opt/bitnami/kafka/config/kafka-init.conf:ro"
  - './kafka-topic-creator.keystore.jks:/opt/bitnami/kafka/config/certs/kafka-topic-creator.keystore.jks:ro'
  - './kafka.truststore.jks:/opt/bitnami/kafka/config/certs/kafka.truststore.jks:ro'
  depends_on:
  - zookeeper
  - kafka
```

In the [kafka-init.sh](distributed/kafka-init.sh) we create the topic as shown below and in the [kafka-init.conf](distributed/kafka-init.conf) we configure the certificates to be able to check that our secured Kafka instance is already running. The topic creating command doesn't need to authenticate against Kafka, it only accesses Zookeeper which doesn't need any authentication in our example. 

The following commands can be executed on Kafka topics using Docker Compose. Please adapt to your environment.

#### List all topics

```sh
docker-compose exec kafka kafka-topics.sh --zookeeper zookeeper:2181 --list
```

#### Create the tx topic

```sh
docker-compose exec kafka kafka-topics.sh --zookeeper zookeeper:2181 --create --if-not-exists --topic tx --partitions 1 --replication-factor 1 --config message.timestamp.type=LogAppendTime --config retention.ms=-1
```

#### Describe the tx topic

```sh
docker-compose exec kafka kafka-topics.sh --zookeeper zookeeper:2181 --describe tx
```

### Cassandra

As explained already above and especially in the [Architecture](../architecture.md) section, Blaze uses Cassandra as external, shared Resource Store.

The Cassandra section of the Docker Compose file, shown below, contains a setup of three Cassandra nodes. In its default setting, Blaze ensures that resources are always written to at least two nodes. So a production setup, will need at least three nodes in order to tolerate a single node failure.

```yaml
cassandra-1:
  image: "docker.io/bitnami/cassandra:3"
  volumes:
  - "./cassandra-init.cql:/docker-entrypoint-initdb.d/cassandra-init.cql:ro"
  - 'cassandra-1-data:/bitnami'
  environment:
    CASSANDRA_SEEDS: "cassandra-1,cassandra-2,cassandra-3"
    CASSANDRA_PASSWORD_SEEDER: "yes"
    MAX_HEAP_SIZE: "4G"
    HEAP_NEWSIZE: "200M"

cassandra-2:
  image: "docker.io/bitnami/cassandra:3"
  volumes:
  - 'cassandra-2-data:/bitnami'
  environment:
    CASSANDRA_SEEDS: "cassandra-1,cassandra-2,cassandra-3"
    MAX_HEAP_SIZE: "4G"
    HEAP_NEWSIZE: "200M"

cassandra-3:
  image: "docker.io/bitnami/cassandra:3"
  volumes:
  - 'cassandra-3-data:/bitnami'
  environment:
    CASSANDRA_SEEDS: "cassandra-1,cassandra-2,cassandra-3"
    MAX_HEAP_SIZE: "4G"
    HEAP_NEWSIZE: "200M"
```

The first Cassandra node contains the following startup script, which initializes the keyspace `blaze` with one table called `resources`:

```text
CREATE KEYSPACE blaze WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor' : 3};
CREATE TABLE blaze.resources (hash text PRIMARY KEY, content blob);
CREATE TABLE blaze.clauses ("token" text PRIMARY KEY, content blob);
```

The keyspace has an replication factor of three, which means that every table row is replicated three times. While a replication factor of two will work for Blaze, a replication factor of three is recommended, because it will allow for two nodes to fail for reads and one node for writes.

You have to create a keyspace and one table for Blaze. You can use the Cassandra Query Language Shell via Docker running the following command:

If you don't like to use the setup script, you can connect to a Cassandra Shell by running:

```sh
docker-compose exec cassandra-1 cqlsh -u cassandra -p cassandra
```

### Blaze

***TODO**: continue

```yaml
blaze-1:
  image: "samply/blaze:0.20"
  hostname: "blaze-1"
  environment:
    JAVA_TOOL_OPTIONS: "-Xmx4g"
    STORAGE: "distributed"
    DB_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
    DB_KAFKA_MAX_REQUEST_SIZE: "3145728"
    DB_KAFKA_SECURITY_PROTOCOL: "SSL"
    DB_KAFKA_SSL_TRUSTSTORE_LOCATION: "/app/kafka.truststore.jks"
    DB_KAFKA_SSL_TRUSTSTORE_PASSWORD: "password"
    DB_KAFKA_SSL_KEYSTORE_LOCATION: "/app/blaze-1.keystore.jks"
    DB_KAFKA_SSL_KEYSTORE_PASSWORD: "password"
    DB_KAFKA_SSL_KEY_PASSWORD: "password"
    DB_CASSANDRA_CONTACT_POINTS: "cassandra-1:9042,cassandra-2:9042,cassandra-3:9042"
    LOG_LEVEL: "debug"
  volumes:
  - './blaze-1.keystore.jks:/app/blaze-1.keystore.jks:ro'
  - './kafka.truststore.jks:/app/kafka.truststore.jks:ro'
  - "blaze-1-data:/app/data"
  deploy:
    resources:
      limits:
        cpus: '4'
  depends_on:
  - kafka-topic-creator
  - cassandra-1
  - cassandra-2
  - cassandra-3

blaze-2:
  image: "samply/blaze:0.20"
  hostname: "blaze-2"
  environment:
    JAVA_TOOL_OPTIONS: "-Xmx4g"
    STORAGE: "distributed"
    DB_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
    DB_KAFKA_MAX_REQUEST_SIZE: "3145728"
    DB_KAFKA_SECURITY_PROTOCOL: "SSL"
    DB_KAFKA_SSL_TRUSTSTORE_LOCATION: "/app/kafka.truststore.jks"
    DB_KAFKA_SSL_TRUSTSTORE_PASSWORD: "password"
    DB_KAFKA_SSL_KEYSTORE_LOCATION: "/app/blaze-2.keystore.jks"
    DB_KAFKA_SSL_KEYSTORE_PASSWORD: "password"
    DB_KAFKA_SSL_KEY_PASSWORD: "password"
    DB_CASSANDRA_CONTACT_POINTS: "cassandra-1:9042,cassandra-2:9042,cassandra-3:9042"
    LOG_LEVEL: "debug"
  volumes:
  - './blaze-2.keystore.jks:/app/blaze-2.keystore.jks:ro'
  - './kafka.truststore.jks:/app/kafka.truststore.jks:ro'
  - "blaze-2-data:/app/data"
  deploy:
    resources:
      limits:
        cpus: '4'
  depends_on:
  - kafka-topic-creator
  - cassandra-1
  - cassandra-2
  - cassandra-3
```

[1]: <http://kafka.apache.org>
[2]: <https://zookeeper.apache.org>
[3]: <https://www.confluent.io/blog/kafka-without-zookeeper-a-sneak-peek/>
[4]: <https://cassandra.apache.org>
[5]: <https://github.com/bitnami/containers/tree/main/bitnami/kafka#security>
[6]: <https://github.com/bitnami/containers/tree/main/bitnami/kafka>
[7]: <http://kafka.apache.org/documentation/#security>
[8]: <http://kafka.apache.org/documentation/>
[9]: <https://raw.githubusercontent.com/confluentinc/confluent-platform-security-tools/master/kafka-generate-ssl.sh>
[10]: <https://www.confluent.io>
[11]: <https://kubernetes.io>
