# Environment Variables

Blaze is configured solely through environment variables. There is a default for every variable. So all variables are optional. 

A part of the environment variables depends on the storage variant chosen. The storage variant can be set through the `STORAGE` env var. The default is `in-memory` for the JAR and `standalone` for the Docker image. The third setting is `distributed`. The following tables list the database relevant environment variables by storage variant.

### In-memory

| Name                           | Default | Since | Depr ¹ | Description                                                                             |
|:-------------------------------|:--------|:------|:-------|:----------------------------------------------------------------------------------------|
| DB_RESOURCE_INDEXER_THREADS    | 4       | v0.8  |        | The number threads used for indexing resources. Try 8 or 16 depending on your hardware. |
| DB_RESOURCE_INDEXER_BATCH_SIZE | 1       | v0.8  | v0.11  | The number of resources which are indexed in a batch. (Deprecated)                      |

¹ Deprecated

### Standalone

The three database directories must not exist on the first start of Blaze and will be created by Blaze itself. It's possible to put this three directories on different disks in order to improve performance.

| Name                           | Default       | Since | Depr ¹ | Description                                                                                                                                      |
|:-------------------------------|:--------------|:------|:-------|:-------------------------------------------------------------------------------------------------------------------------------------------------|
| INDEX_DB_DIR                   | index ²       | v0.8  |        | The directory were the index database files are stored.                                                                                          |
| INDEX_DB_WAL_DIR               | \<empty\>     | v0.18 |        | The directory were the index database write ahead log (WAL) files are stored. Empty means same dir as database files.                            |
| TRANSACTION_DB_DIR             | transaction ² | v0.8  |        | The directory were the transaction log files are stored. This directory must not exist on the first start of Blaze and will be created by Blaze. |
| TRANSACTION_DB_WAL_DIR         | \<empty\>     | v0.18 |        | The directory were the transaction log write ahead log (WAL) files are stored. Empty means same dir as database files.                           |
| RESOURCE_DB_DIR                | resource ²    | v0.8  |        | The directory were the resource files are stored. This directory must not exist on the first start of Blaze and will be created by               |
| RESOURCE_DB_WAL_DIR            | \<empty\>     | v0.18 |        | The directory were the resource write ahead log (WAL) files are stored. Empty means same dir as database files.                                  |
| DB_BLOCK_CACHE_SIZE            | 128           | v0.8  |        | The size of the [block cache][2] of the DB in MB.                                                                                                |
| DB_RESOURCE_CACHE_SIZE         | 100000        | v0.8  |        | The size of the resource cache of the DB in number of resources.                                                                                 |
| DB_MAX_BACKGROUND_JOBS         | 4             | v0.8  |        | The maximum number of the [background jobs][3] used for DB compactions.                                                                          |
| DB_RESOURCE_INDEXER_THREADS    | 4             | v0.8  |        | The number threads used for indexing resources. Try 8 or 16 depending on your hardware.                                                          |
| DB_RESOURCE_INDEXER_BATCH_SIZE | 1             | v0.8  | v0.11  | The number of resources which are indexed in a batch. (Deprecated)                                                                               |
| DB_RESOURCE_STORE_KV_THREADS   | 4             | v0.17 |        | The number threads used for reading and writing resources.                                                                                       |

¹ Deprecated, ² In the JAR variant. The Docker image uses a directory below the `/app/data` directory.

### Distributed

The distributed storage variant only uses the index database locally. 

| Name                               | Default        | Since | Depr ¹ | Description                                                                                                                                          |
|:-----------------------------------|:---------------|:------|:-------|:-----------------------------------------------------------------------------------------------------------------------------------------------------|
| INDEX_DB_DIR                       | index ²        | v0.8  |        | The directory were the index database files are stored.                                                                                              |
| INDEX_DB_WAL_DIR                   | \<empty\>      | v0.18 |        | The directory were the index database write ahead log (WAL) files are stored. Empty means same dir as database files.                                |
| DB_BLOCK_CACHE_SIZE                | 128            | v0.8  |        | The size of the [block cache][2] of the DB in MB.                                                                                                    |
| DB_RESOURCE_CACHE_SIZE             | 100000         | v0.8  |        | The size of the resource cache of the DB in number of resources.                                                                                     |
| DB_MAX_BACKGROUND_JOBS             | 4              | v0.8  |        | The maximum number of the [background jobs][3] used for DB compactions.                                                                              |
| DB_RESOURCE_INDEXER_THREADS        | 4              | v0.8  |        | The number threads used for indexing resources. Try 8 or 16 depending on your hardware.                                                              |
| DB_RESOURCE_INDEXER_BATCH_SIZE     | 1              | v0.8  | v0.11  | The number of resources which are indexed in a batch. (Deprecated)                                                                                   |
| DB_RESOURCE_STORE_KV_THREADS       | 4              | v0.17 |        | The number threads used for reading and writing resources.                                                                                           |
| DB_KAFKA_BOOTSTRAP_SERVERS         | localhost:9092 | v0.8  |        | A comma separated list of bootstrap servers for the Kafka transaction log.                                                                           |
| DB_KAFKA_MAX_REQUEST_SIZE          | 1048576        | v0.8  |        | The maximum size of a encoded transaction able to send to the Kafka transaction log in bytes.                                                        |
| DB_KAFKA_COMPRESSION_TYPE          | snappy         | v0.11 |        | The compression type for transaction data generated by the producer. Valid values are `none`, `gzip`, `snappy`, `lz4`, or `zstd`.                    |
| DB_KAFKA_SECURITY_PROTOCOL         | PLAINTEXT      | v0.11 |        | Protocol used to communicate with brokers. Valid values are: PLAINTEXT and SSL.                                                                      |
| DB_KAFKA_SSL_TRUSTSTORE_LOCATION   | —              | v0.11 |        | The location of the trust store file.                                                                                                                |
| DB_KAFKA_SSL_TRUSTSTORE_PASSWORD   | —              | v0.11 |        | The password for the trust store file. If a password is not set, trust store file configured will still be used, but integrity checking is disabled. |
| DB_KAFKA_SSL_KEYSTORE_LOCATION     | —              | v0.11 |        | The location of the key store file. This is optional for client and can be used for two-way authentication for client.                               |
| DB_KAFKA_SSL_KEYSTORE_PASSWORD     | —              | v0.11 |        | The store password for the key store file. This is optional for client and only needed if DB_KAFKA_SSL_KEYSTORE_LOCATION is configured.              |
| DB_KAFKA_SSL_KEY_PASSWORD          | —              | v0.11 |        | The password of the private key in the key store file. This is required for clients only if two-way authentication is configured.                    |
| DB_CASSANDRA_CONTACT_POINTS        | localhost:9042 | v0.8  |        | A comma separated list of contact points for the Cassandra resource store.                                                                           |
| DB_CASSANDRA_USERNAME              | cassandra      | v0.11 |        | The username for the Cassandra authentication.                                                                                                       |
| DB_CASSANDRA_PASSWORD              | cassandra      | v0.11 |        | The password for the Cassandra authentication.                                                                                                       |
| DB_CASSANDRA_KEY_SPACE             | blaze          | v0.8  |        | The Cassandra key space were the `resources` table is located.                                                                                       |
| DB_CASSANDRA_PUT_CONSISTENCY_LEVEL | TWO            | v0.8  |        | Cassandra consistency level for resource put (insert) operations. Has to be set to `ONE` on a non-replicated keyspace.                               |
| DB_CASSANDRA_REQUEST_TIMEOUT       | 2000           | v0.11 |        | Timeout in milliseconds for all requests to the Cassandra cluster.                                                                                   |

¹ Deprecated, ² In the JAR variant. The Docker image uses a directory below the `/app/data` directory.

More information about distributed deployment are available [here](distributed.md). 

### Other Environment Variables

| Name                                    | Default                    | Since  | Depr ¹ | Description                                                                                    |
|:----------------------------------------|:---------------------------|:-------|--------|:-----------------------------------------------------------------------------------------------|
| PROXY_HOST                              | —                          | v0.6   | —      | REMOVED: use -Dhttp.proxyHost                                                                  |
| PROXY_PORT                              | —                          | v0.6   | —      | REMOVED: use -Dhttp.proxyPort                                                                  |
| PROXY_USER                              | —                          | v0.6.1 | —      | REMOVED: try [SOCKS Options][1]                                                                |
| PROXY_PASSWORD                          | —                          | v0.6.1 | —      | REMOVED: try [SOCKS Options][1]                                                                |
| CONNECTION_TIMEOUT                      | 5 s                        | v0.6.3 | —      | connection timeout for outbound HTTP requests                                                  |
| REQUEST_TIMEOUT                         | 30 s                       | v0.6.3 | —      | REMOVED                                                                                        |
| TERM_SERVICE_URI                        | [http://tx.fhir.org/r4][6] | v0.6   | v0.11  | Base URI of the terminology service                                                            |
| BASE_URL                                | `http://localhost:8080`    | —      | —      | The URL under which Blaze is accessible by clients.                                            |
| CONTEXT_PATH                            | /fhir                      | v0.11  | —      | Context path under which the FHIR RESTful API will be accessible.                              |
| SERVER_PORT                             | 8080                       | —      | —      | The port of the main HTTP server                                                               |
| METRICS_SERVER_PORT                     | 8081                       | v0.6   | —      | The port of the Prometheus metrics server                                                      |
| LOG_LEVEL                               | info                       | v0.6   | —      | one of trace, debug, info, warn or error                                                       |
| JAVA_TOOL_OPTIONS                       | —                          | —      | —      | JVM options \(Docker only\)                                                                    |
| FHIR_OPERATION_EVALUATE_MEASURE_THREADS | 4                          | v0.8   | —      | The maximum number of parallel $evaluate-measure executions.                                   |
| FHIR_OPERATION_EVALUATE_MEASURE_TIMEOUT | 3600000 (1h)               | v0.19  | —      | Timeout in milliseconds for $evaluate-measure executions.                                      |
| OPENID_PROVIDER_URL                     | —                          | v0.11  | —      | [OpenID Connect][4] provider URL to enable [authentication][5]                                 |
| ENFORCE_REFERENTIAL_INTEGRITY           | true                       | v0.14  | —      | Enforce referential integrity on resource create, update and delete.                           |
| DB_SYNC_TIMEOUT                         | 10000                      | v0.15  | —      | Timeout in milliseconds for all reading FHIR interactions acquiring the newest database state. |

¹ Deprecated

#### BASE_URL

The [FHIR RESTful API](https://www.hl7.org/fhir/http.html) will be accessible under `BASE_URL/CONTEXT_PATH`. Possible X-Forwarded-Host, X-Forwarded-Proto and Forwarded request headers will override this URL.

#### FHIR_OPERATION_EVALUATE_MEASURE_THREADS

Not the same as the number of threads used for measure evaluation which equal to the number of available processors.

#### ENFORCE_REFERENTIAL_INTEGRITY

It's enabled by default but can be disabled on proxy/middleware/secondary systems were a primary system ensures referential integrity.

#### DB_SYNC_TIMEOUT

All reading FHIR interactions have to acquire the last database state known at the time the request arrived in order to ensure [consistency](../consistency.md). That database state might not be ready immediately because indexing might be still undergoing. In such a situation, the request has to wait for the database state becoming available. If the database state won't be available before the timeout expires, a 503 Service Unavailable response will be returned. Please increase this timeout if you experience such 503 responses, and you are not able to improve indexing performance or lower transaction load.  

### Common JAVA_TOOL_OPTIONS

| Name             | Default | Since | Description                                                  |
|:-----------------|:--------|:------|:-------------------------------------------------------------|
| -Xmx4g           | -       |       | The maximum amount of heap memory.                           |
| -Dhttp.proxyHost | -       | v0.11 | The hostname of the proxy server for outbound HTTP requests. |
| -Dhttp.proxyPort | 80      | v0.11 | The port of the proxy server.                                |

[1]: <https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/doc-files/net-properties.html#Proxies>
[2]: <https://github.com/facebook/rocksdb/wiki/Setup-Options-and-Basic-Tuning#block-cache-size>
[3]: <https://github.com/facebook/rocksdb/wiki/Thread-Pool>
[4]: <https://openid.net/connect/>
[5]: <../authentication.md>
[6]: <http://tx.fhir.org/r4>
