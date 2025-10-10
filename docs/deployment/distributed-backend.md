# Distributed

Blaze can be deployed in three different storage variants: in-memory, standalone and distributed. In this section, the deployment of the distributed storage variant is described.

The distributed storage variant should be used for production setups that require high- availability and/or horizontal read scalability. 

As described in the [Architecture](../architecture.md) section, Blaze roughly consists of three components, the Blaze Node itself, a Transaction Log and a Resource Store. With the standalone storage variant, both the Transaction Log and the Resource Store are embedded inside the Blaze process. However using the distributed storage variant, the Transaction Log and the Resource Store are extern to Blaze and allow more than one Blaze Node to run. Being able to run more than one Blaze Node enables high availability and horizontal read scalability.

## Docker Compose Example

The `docs/deployment/distributed` directory contains a Docker Compose file with an example system consisting of all components needed in a distributed setup. 

Please be aware that we **don't recommend** to run Blaze on a single server using our example setup. You most likely will run the individual components in a [Kubernetes][11] Cluster or on separate VM's. The main purpose of this setup is to explain the components and their configuration in detail. Your production setup will and has to differ from this example in order to accomplish your high-availability, security and scalability goals.

You will need about 32 GB of RAM to be able to run the example smoothly. You can start it by going into the `docs/deployment/distributed` directory and run:

```sh
docker compose up -d
```

Currently, Blaze uses [Kafka][1] for the Transaction Log and [Cassandra][4] for the Resource Store, so the example uses the same. A walk-through of the components follows:

### Kafka

As explained already above and especially in the [Architecture](../architecture.md) section, Blaze uses one single Kafka topic to coordinate its transactions between multiple nodes.

The Kafka section of the Docker Compose file, shown below, contains a setup of a single Kafka node. In a production ready setup you most likely will run three Kafka nodes in order to replicate the messages and so making Kafka high available itself.

```yaml
kafka:
  image: "apache/kafka:4.0.0"
  environment:
    KAFKA_NODE_ID: 1
    CLUSTER_ID: '5L6g3nShT-eMCtK--X86sw'
    # KRaft
    KAFKA_PROCESS_ROLES: "broker,controller"
    KAFKA_CONTROLLER_QUORUM_VOTERS: '1@localhost:29093'
    KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
    KAFKA_INTER_BROKER_LISTENER_NAME: 'SSL-INTERNAL'
    # Listeners
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "SSL:SSL,CONTROLLER:PLAINTEXT,SSL-INTERNAL:SSL"
    KAFKA_LISTENERS: "SSL://:9092,CONTROLLER://:29093,SSL-INTERNAL://:19093"
    KAFKA_ADVERTISED_LISTENERS: "SSL://kafka:9092,SSL-INTERNAL://kafka:19093"
    KAFKA_SSL_KEYSTORE_FILENAME: "kafka.keystore.jks"
    KAFKA_SSL_KEY_CREDENTIALS: "credentials"
    KAFKA_SSL_KEYSTORE_CREDENTIALS: "credentials"
    KAFKA_SSL_TRUSTSTORE_FILENAME: "kafka.truststore.jks"
    KAFKA_SSL_TRUSTSTORE_CREDENTIALS: "credentials"
    KAFKA_SSL_CLIENT_AUTH: 'required'
    # It's important to create the tx topic ourselves, because it needs to use
    # LogAppendTime timestamps
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
  volumes:
  - "./kafka.keystore.jks:/etc/kafka/secrets/kafka.keystore.jks:ro"
  - "./kafka.truststore.jks:/etc/kafka/secrets/kafka.truststore.jks:ro"
  - "./credentials:/etc/kafka/secrets/credentials:ro"
  - "kafka-data:/var/lib/kafka/data"
  healthcheck:
    test: nc -z localhost 9092 || exit -1
    start_period: 15s
    interval: 5s
    timeout: 10s
    retries: 10
```

Regarding authentication and transport, we choose to use Kafka's SSL transport and client certificates in order to demonstrate a production ready connection from Blaze to Kafka in our example. The SSL configuration was taken from the [SSL Example][5] of the Kafka Docker documentation. Another good source of documentation can be found in the [Security section][7] of the [Kafka documentation][8]. The Java Truststore and Keystore were created with the [kafka-generate-ssl.sh][9] script provided by [Confluent][10]. Again, you will most likely have other ways to generate your keys in production.

Kafka itself will start with no topics created and we disabled automatic topic creation with purpose, by setting `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE` to `false`, because Blaze needs a topic with specific properties.

### Kafka Topic Creator

The purpose of the Kafka Topic Creator creator component shown below is solely to create the transaction topic Blaze needs. In your production environment, you will most likely create the topic yourself.

```yaml
kafka-topic-creator-main:
  image: "apache/kafka:4.0.0"
  command: "/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 
      --command-config /etc/kafka/kafka-init.conf --create --if-not-exists 
      --topic blaze-tx-main --partitions 1 --replication-factor 1 
      --config message.timestamp.type=LogAppendTime --config retention.ms=-1"
  volumes:
  - "./kafka-init.conf:/etc/kafka/kafka-init.conf:ro"
  - "./kafka-topic-creator.keystore.jks:/etc/kafka/secrets/kafka-topic-creator.keystore.jks:ro"
  - "./kafka.truststore.jks:/etc/kafka/secrets/kafka.truststore.jks:ro"
  depends_on:
    kafka:
      condition: service_healthy
```

In the Docker Compose command we create the topic as shown below and in the [kafka-init.conf](distributed/kafka-init.conf) we configure the certificates to be able to communicate with Kafka.

### Cassandra

As explained already above and especially in the [Architecture](../architecture.md) section, Blaze uses Cassandra as external, shared Resource Store.

The Cassandra section of the Docker Compose file, shown below, contains a setup of two Cassandra nodes. In its default setting, Blaze ensures that resources are always written to at least two nodes. So a production setup, will need at least three nodes in order to tolerate a single node failure.

```yaml
cassandra-1:
  image: "cassandra:4.1.4"
  volumes:
  - "cassandra-1-data:/var/lib/cassandra"
  environment:
    CASSANDRA_SEEDS: "cassandra-1,cassandra-2"
    MAX_HEAP_SIZE: "512M"
    HEAP_NEWSIZE: "100M"
  healthcheck:
    test: ["CMD", "cqlsh", "-e", "describe keyspaces"]
    start_period: 45s
    interval: 5s
    timeout: 10s
    retries: 10
```

### Cassandra Data Initialization

The Cassandra data initialization service contains the following startup script, which initializes the keyspace `blaze_main` and `blaze_admin` with two tables called `resources` and `clauses`:

```yaml
cassandra-init-data:
  image: "cassandra:4.1.4"
  command: "cqlsh -f /scripts/cassandra-init.cql"
  environment:
    CQLSH_HOST: "cassandra-1"
  volumes:
  - "./cassandra-init.cql:/scripts/cassandra-init.cql:ro"
  depends_on:
    cassandra-1:
      condition: service_healthy
    cassandra-2:
      condition: service_healthy
```

```text
CREATE KEYSPACE blaze WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor' : 2};
CREATE TABLE blaze.resources (hash text PRIMARY KEY, content blob);
CREATE TABLE blaze.clauses ("token" text PRIMARY KEY, content blob);
```

The keyspace has an replication factor of two, which means that every table row is replicated two times. While a replication factor of two will work for Blaze, a replication factor of three is recommended, because it will allow for two nodes to fail for reads and one node for writes.

### Blaze

***TODO**: continue

```yaml
blaze-1:
  image: "blaze:latest"
  environment:
    JAVA_TOOL_OPTIONS: "-Xmx1g"
    STORAGE: "distributed"
    DB_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
    DB_KAFKA_MAX_REQUEST_SIZE: "10485760"
    DB_KAFKA_SECURITY_PROTOCOL: "SSL"
    DB_KAFKA_SSL_TRUSTSTORE_LOCATION: "/app/kafka.truststore.jks"
    DB_KAFKA_SSL_TRUSTSTORE_PASSWORD: "password"
    DB_KAFKA_SSL_KEYSTORE_LOCATION: "/app/blaze.keystore.jks"
    DB_KAFKA_SSL_KEYSTORE_PASSWORD: "password"
    DB_KAFKA_SSL_KEY_PASSWORD: "password"
    DB_CASSANDRA_CONTACT_POINTS: "cassandra-1:9042,cassandra-2:9042"
    DB_CASSANDRA_REQUEST_TIMEOUT: "60000"
    DB_CASSANDRA_MAX_CONCURRENT_REQUESTS: "128"
    ENABLE_ADMIN_API: "true"
    LOG_LEVEL: "debug"
  ports:
  - "8081:8081"
  volumes:
  - "./blaze.keystore.jks:/app/blaze.keystore.jks:ro"
  - "./kafka.truststore.jks:/app/kafka.truststore.jks:ro"
  - "blaze-1-data:/app/data"
  depends_on:
    kafka-topic-creator-main:
      condition: service_completed_successfully
    kafka-topic-creator-admin:
      condition: service_completed_successfully
    cassandra-init-data:
      condition: service_completed_successfully
  healthcheck:
    test: [ "CMD", "wget", "--spider", "http://localhost:8080/health" ]
    interval: 10s
    timeout: 5s
    retries: 5
    start_period: 30s
```

### Health Check

The command `wget` is available and can be used for implementing a health check on the `/health` endpoint which is separate from the `/fhir` endpoint.

> [!CAUTION]
> The command `curl` was never officially available in the Blaze backend image. It will be removed in version 1.6. Please migrate to use `wget`.

[1]: <http://kafka.apache.org>
[4]: <https://cassandra.apache.org>
[5]: <https://github.com/apache/kafka/blob/trunk/docker/examples/jvm/single-node/ssl/docker-compose.yml>
[7]: <http://kafka.apache.org/documentation/#security>
[8]: <http://kafka.apache.org/documentation/>
[9]: <https://raw.githubusercontent.com/confluentinc/confluent-platform-security-tools/master/kafka-generate-ssl.sh>
[10]: <https://www.confluent.io>
[11]: <https://kubernetes.io>
