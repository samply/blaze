# Blaze

[![Build Status](https://travis-ci.com/samply/blaze.svg?branch=release-0.8)](https://travis-ci.com/samply/blaze)
[![Docker Pulls](https://img.shields.io/docker/pulls/samply/blaze.svg)](https://hub.docker.com/r/samply/blaze/)
[![Image Layers](https://images.microbadger.com/badges/image/samply/blaze.svg)](https://microbadger.com/images/samply/blaze)
[![codecov](https://codecov.io/gh/samply/blaze/branch/develop/graph/badge.svg)](https://codecov.io/gh/samply/blaze)

A FHIR® Store with internal, fast CQL Evaluation Engine

## Goal

The goal of this project is to provide a FHIR® Store with an internal CQL Evaluation Engine which is able to answer population wide aggregate queries in a timely manner to enable interactive, online queries.

## State

The project is currently under active development. Essentially all official [CQL Tests][3] pass. Please report any issues you encounter during evaluation.

Latest release: [v0.9.0-alpha.10][5]

## Quick Start

In order to run Blaze just execute the following:

### Docker

```bash
docker volume create blaze-data
docker run -p 8080:8080 -v blaze-data:/app/data samply/blaze:0.9.0-alpha.27
```

Blaze will create multiple directories inside the `blaze-data` volume on its first start and use the same directories on subsequent starts.


### Standalone Java without Docker

In case Docker isn't available, Blaze can be run on a machine having OpenJDK 11 installed. Blaze is tested with [AdoptOpenJDK][11].

```bash
wget https://github.com/samply/blaze/releases/download/v0.9.0-alpha.10/blaze-0.9.0-alpha.27-standalone.jar
java -jar blaze-0.9.0-alpha.27-standalone.jar -m blaze.core
```

Blaze will run with an in-memory, volatile database for testing and demo purposes.

Blaze can be run with durable storage by setting the environment variables `STORAGE` to `standalone`. 

Under Linux/macOS:

```bash
STORAGE=standalone java -jar blaze-0.9.0-alpha.27-standalone.jar -m blaze.core
```

Under Windows:
 
```bash
$Env:STORAGE="standalone"
java -jar blaze-0.9.0-alpha.27-standalone.jar -m blaze.core
```

This will create three directories called `index`, `transaction` and `resource` inside the current working directory, one for each database part used.

Logging output should appear which prints the most important settings and system parameters like Java version and available memory.

In order to test connectivity, query the health endpoint:

```bash
curl http://localhost:8080/health
```

## Configuration

Blaze is configured solely through environment variables. There is a default for every variable. So all are optional. 

A part of the environment variables depends on the storage variant chosen. The storage variant can be set through the `STORAGE` env var. The default is `in-memory` for the JAR and `standalone` for the Docker image. The third setting is `distributed`. The following tables list the database relavant environment variables by storage variant.

### In-memory

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| DB\_RESOURCE\_INDEXER\_BATCH\_SIZE | 1 | v0.8 | The number of resources which are indexed in a batch. |

### Standalone

The three database directories must not exist on the first start of Blaze and will be created by Blaze itself. It's possible to put this three directories on different disks in order to improve performance.

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| INDEX\_DB\_DIR | – | v0.8 | The directory were the index database files are stored.  |
| TRANSACTION\_DB\_DIR | – | v0.8 | The directory were the transaction log files are stored. This directory must not exist on the first start of Blaze and will be created by Blaze. |
| RESOURCE\_DB\_DIR | – | v0.8 | The directory were the resource files are stored. This directory must not exist on the first start of Blaze and will be created by 
| DB\_RESOURCE\_INDEXER\_BATCH\_SIZE | 1 | v0.8 | The number of resources which are indexed in a batch. |
| DB\_BLOCK\_CACHE\_SIZE | 128 | v0.8 | The size of the [block cache][9] of the DB in MB. |
| DB\_RESOURCE\_CACHE\_SIZE | 10000 | v0.8 | The size of the resource cache of the DB in number of resources. |
| DB\_MAX\_BACKGROUND\_JOBS | 4 | v0.8 | The maximum number of the [background jobs][10] used for DB compactions. |
| DB\_RESOURCE\_INDEXER\_THREADS | 4 | v0.8 | The number threads used for indexing resources. |
| DB\_RESOURCE\_INDEXER\_BATCH\_SIZE | 1 | v0.8 | The number of resources which are indexed in a batch. |
 
### Distributed

The distributed storage variant only uses the index database locally. 

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| INDEX\_DB\_DIR | – | v0.8 | The directory were the index database files are stored.  |
| DB\_BLOCK\_CACHE\_SIZE | 128 | v0.8 | The size of the [block cache][9] of the DB in MB. |
| DB\_RESOURCE\_CACHE\_SIZE | 10000 | v0.8 | The size of the resource cache of the DB in number of resources. |
| DB\_MAX\_BACKGROUND\_JOBS | 4 | v0.8 | The maximum number of the [background jobs][10] used for DB compactions. |
| DB\_RESOURCE\_INDEXER\_THREADS | 4 | v0.8 | The number threads used for indexing resources. |
| DB\_RESOURCE\_INDEXER\_BATCH\_SIZE | 1 | v0.8 | The number of resources which are indexed in a batch. |
| DB\_KAFKA\_BOOTSTRAP\_SERVERS | localhost:9092 | v0.8 | A comma separated list of bootstrap servers for the Kafka transaction log. |
| DB\_KAFKA\_MAX\_REQUEST\_SIZE | 1048576 | v0.8 | The maximum size of a encoded transaction able to send to the Kafka transaction log in bytes. |
| DB\_CASSANDRA\_CONTACT\_POINTS | localhost:9042 | v0.8 | A comma separated list of contact points for the Cassandra resource store. |
| DB\_CASSANDRA\_KEY\_SPACE | blaze | v0.8 | The Cassandra key space were the `resources` table is located. |
| DB\_CASSANDRA\_PUT\_CONSISTENCY\_LEVEL | TWO | v0.8 | Cassandra consistency level for resource put (insert) operations. Has to be set to `ONE` on a non-replicated keyspace. |


### Other Environment Variables

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| PROXY\_HOST | — | v0.6 | The hostname of the proxy server for outbound HTTP requests |
| PROXY\_PORT | — | v0.6 | Port of the proxy server |
| PROXY\_USER | — | v0.6.1 | Proxy server user, if authentication is needed. |
| PROXY\_PASSWORD | — | v0.6.1 | Proxy server password, if authentication is needed. |
| CONNECTION\_TIMEOUT | 5 s | v0.6.3 | connection timeout for outbound HTTP requests |
| REQUEST\_TIMEOUT | 30 s | v0.6.3 | request timeout for outbound HTTP requests |
| TERM\_SERVICE\_URI | [http://tx.fhir.org/r4](http://tx.fhir.org/r4) | v0.6 | Base URI of the terminology service |
| BASE\_URL | [http://localhost:8080](http://localhost:8080) |  | The URL under which Blaze is accessible by clients. The [FHIR RESTful API](https://www.hl7.org/fhir/http.html) will be accessible under `BASE_URL/fhir`. |
| SERVER\_PORT | 8080 |  | The port of the main HTTP server |
| METRICS\_SERVER\_PORT | 8081 | v0.6 | The port of the Prometheus metrics server |
| LOG\_LEVEL | info | v0.6 | one of trace, debug, info, warn or error |
| JAVA\_TOOL\_OPTIONS | — |  | JVM options \(Docker only\) |
| FHIR\_OPERATION\_EVALUATE\_MEASURE\_THREADS | 4 | v0.8 | The maximum number of parallel $evaluate-measure executions. Not the same as the number of threads used for measure evaluation which equal to the number of available processors. |

## Storage Variants

### Distributed

#### Kafka

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

#### Cassandra

Cassandra can be used as resource storage. You have to create a keyspace and one table for Blaze. You can use the Cassandra Query Language Shell via Docker running the following command:

```bash
docker run -it --rm --network cassandra_default cassandra:3 cqlsh cassandra
```

##### Keyspace

```
create keyspace blaze WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 3};
```

##### Table

```
CREATE TABLE blaze.resources (hash text PRIMARY KEY, content blob);
```

## Tuning Guide

### Common Considerations

#### CPU Cores

Blaze heavily benefits from vertical scaling (number of CPU cores). Even with single clients, transactions and CQL queries scale with the number of CPU cores. Performance tests show that 32 cores can be utilized easily.

#### Memory

Blaze uses three main memory regions, namely the JVM heap memory, the RocksDB block cache and the OS page cache. The JVM heap memory is used for general operation and specifically for the resource cache which caches resources in their ready to use object form. The RocksDB block cache is separate from the JVM heap memory because it's implemented as off-heap memory region. The block cache stores resources and index segments in uncompressed blob form. After the resource cache, it functions as a second level cache which stores resources in a more compact form and is not subject to JVM garbage collection. The OS page cache functions as a third level of caching, because it contains all recently access database files. The database files are compressed and so store data in an even more compact form.

The first recommendation is to leave half of the available system memory for the OS page cache. For example if you have 16 GB of memory, you should only allocate 8 GB for JVM heap + RocksDB block cache. The ratio between the JVM heap size and the RocksDB block cache can vary. First you should monitor the JVM heap usage and garbage collection activity in your use case. Blaze provides a Prometheus metrics endpoint on port 8081 per default. Using half of the remaining system memory for the JVM heap and half for the RocksDB block cache will be a good start.

Depending on the capabilities of your disk I/O system, having enough OS page cache for holding most of the active database file size, can really make a huge difference in performance. In case all your database files fit in memory, you have essentially a in-memory database. Test with up to 128 GB system memory showed that effect.

#### Disk I/O

Blaze is designed with local (NVMe) SSD storage in mind. That is the reason why Blaze uses RocksDB as underlying storage engine. RocksDB benefits from fast storage. The idea is that latency of local SSD storage is faster than the network. So remote network storage has no longer an advantage.

If you have only spinning disks available, a bigger OS page cache can help. Otherwise especially the latency of operations will suffer.

### Transactions

Blaze maintains indexes for FHIR search parameters and CQL evaluation. The indexing process can be executed in parallel if transactions contain more than one resource. Transaction containing only one resource and direct REST interactions like create, update or delete don't benefit from parallel indexing.

Depending on your system, indexing can be I/O or CPU bound. Performance tests show, that at least 20 CPU cores can be utilized for resource indexing. The default number of indexing threads is 4 but can be changed by setting `DB_RESOURCE_INDEXER_THREADS` to a different value. However only transactions with more than `DB_RESOURCE_INDEXER_THREADS` resources will benefit from it.

If your transactions contain way more resources as your number of CPU cores available, you could experiment with increasing the `DB_RESOURCE_INDEXER_BATCH_SIZE` which has a default value of 1. With a value of more than one, the resource indexer will create batches of resources which are indexed together by one thread. This batching lowers the coordination overhead. But be aware that a batch size of about your transaction size will prevent any parallelism.

### CQL Queries



## Deployment

In-deep deployment options of Blaze are described in the [Deployment Section][4] of the Blaze documentation.

## YourKit Profiler

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

The developers of Blaze uses the YourKit profiler to optimize performance. YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler][6], [YourKit .NET Profiler][7] and [YourKit YouMonitor][8].

## License

Copyright 2019 The Samply Development Community

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[3]: <https://cql.hl7.org/tests.html>
[4]: <https://alexanderkiel.gitbook.io/blaze/deployment>
[5]: <https://github.com/samply/blaze/releases/tag/v0.9.0-alpha.10>
[6]: <https://www.yourkit.com/java/profiler/>
[7]: <https://www.yourkit.com/.net/profiler/>
[8]: <https://www.yourkit.com/youmonitor/>
[9]: <https://github.com/facebook/rocksdb/wiki/Setup-Options-and-Basic-Tuning#block-cache-size>
[10]: <https://github.com/facebook/rocksdb/wiki/RocksDB-Basics#multi-threaded-compactions>
[11]: <https://adoptopenjdk.net>
