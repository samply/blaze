# Configuration

## Frontend

### Environment Variables

#### `ORIGIN` <Badge type="warning" text="Since 0.26"/>

The base URL of the frontend as accessed by the browser.

#### `BACKEND_BASE_URL` <Badge type="warning" text="Since 0.26"/>

The `BASE_URL` of the backend as reachable by the frontend.

#### `AUTH_CLIENT_ID` <Badge type="warning" text="Since 0.26"/>

The client ID of the OAuth client used to communicate with the auth provider.

#### `AUTH_CLIENT_SECRET` <Badge type="warning" text="Since 0.26"/>

The client secret of the OAuth client used to communicate with the auth provider.

#### `AUTH_ISSUER` <Badge type="warning" text="Since 0.26"/>

The base URL of the auth provider. For Keycloak that is the realm base URL.

#### `AUTH_SECRET` <Badge type="warning" text="Since 0.26"/>

A secret random string that is used to encrypt the session cookie. This should be a minimum of 32 characters, random string. On UNIX systems you can use openssl rand -hex 32 to generate one.

#### `PROTOCOL_HEADER` <Badge type="warning" text="Since 0.26"/>

Set this to `x-forwarded-proto` if the frontend is deployed behind a reverse proxy.

#### `HOST_HEADER` <Badge type="warning" text="Since 0.26"/>

Set this to `x-forwarded-host` if the frontend is deployed behind a reverse proxy.

#### `NODE_EXTRA_CA_CERTS` <Badge type="warning" text="Since 0.26"/>

The name of a file with additional CA certificates needed to access especially the auth provider.

## Backend

Blaze backend is configured solely through environment variables. There is a default for every variable. So all variables are optional. 

Some of the environment variables depend on the storage variant chosen. The storage variant can be set through the `STORAGE` env var. The default is `standalone,` with `in-memory` and `distributed` as the other options. The following sections list the relevant environment variables by storage variant.

### Standalone

The three database directories must not exist on the first start of Blaze and will be created by Blaze itself. It's possible to put this three directories on different disks in order to improve performance.

#### `INDEX_DB_DIR` <Badge type="warning" text="Since 0.8"/>

The directory were the index database files are stored.                                                                                          |

**Default:** `/app/data/index`

#### `INDEX_DB_WAL_DIR` <Badge type="warning" text="Since 0.18"/>

The directory were the index database write ahead log (WAL) files are stored.

**Default:** Same dir as index database files.

#### `TRANSACTION_DB_DIR` <Badge type="warning" text="Since 0.8"/>

The directory were the transaction log files are stored. This directory must not exist on the first start of Blaze and will be created by Blaze.

**Default:** `/app/data/transaction`

#### `TRANSACTION_DB_WAL_DIR` <Badge type="warning" text="Since 0.18"/>

The directory were the transaction log write ahead log (WAL) files are stored.

**Default:** Same dir as transaction database files.

#### `RESOURCE_DB_DIR` <Badge type="warning" text="Since 0."/>

The directory were the resource files are stored. This directory must not exist on the first start of Blaze and will be created by.

**Default:** `/app/data/resource`

#### `RESOURCE_DB_WAL_DIR` <Badge type="warning" text="Since 0.18"/>

The directory were the resource write ahead log (WAL) files are stored.

**Default:** Same dir as resource database files.

#### `DB_BLOCK_CACHE_SIZE` <Badge type="warning" text="Since 0.8"/>

The size of the [block cache][2] of the DB in MiB. This cache is outside of the JVM heap.

**Default:** 128

#### `DB_RESOURCE_CACHE_SIZE` <Badge type="warning" text="Since 0.8"/>

The size of the resource cache of the DB in number of resources.

**Default:** 100000

#### `DB_MAX_BACKGROUND_JOBS` <Badge type="warning" text="Since 0.8"/>

The maximum number of the [background jobs][3] used for DB compactions.

**Default:** 4

#### `DB_RESOURCE_INDEXER_THREADS` <Badge type="warning" text="Since 0.8"/>

The number threads used for indexing resources. Try 8 or 16 depending on your hardware.

**Default:** 4

#### `DB_RESOURCE_INDEXER_BATCH_SIZE` <Badge type="warning" text="Since 0.8"/> <Badge type="danger" text="Deprecated 0.11"/>

The number of resources which are indexed in a batch. (Deprecated)

**Default:** 1

#### `DB_RESOURCE_STORE_KV_THREADS` <Badge type="warning" text="Since 0.17"/>

The number of threads used for reading and writing resources.

**Default:** 4

### In-memory

#### `DB_RESOURCE_INDEXER_THREADS` <Badge type="warning" text="Since 0.8"/>

The number threads used for indexing resources. Try 8 or 16 depending on your hardware.

**Default:** 4

#### `DB_RESOURCE_INDEXER_BATCH_SIZE` <Badge type="warning" text="Since 0.8"/> <Badge type="danger" text="Deprecated 0.11"/>

The number of resources which are indexed in a batch. (Deprecated)

**Default:** 1

### Distributed

The distributed storage variant only uses the index database locally. 

#### `INDEX_DB_DIR` <Badge type="warning" text="Since 0.8"/>

The directory were the index database files are stored.

**Default:** `/app/data/index`

#### `INDEX_DB_WAL_DIR` <Badge type="warning" text="Since 0.18"/>

The directory were the index database write ahead log (WAL) files are stored.

**Default:** Same dir as index database files.

#### `DB_BLOCK_CACHE_SIZE` <Badge type="warning" text="Since 0.8"/>

The size of the [block cache][2] of the DB in MiB. This cache is outside of the JVM heap.

**Default:** 128

#### `DB_RESOURCE_CACHE_SIZE` <Badge type="warning" text="Since 0.8"/>

The size of the resource cache of the DB in number of resources.

**Default:** 100000

#### `DB_MAX_BACKGROUND_JOBS` <Badge type="warning" text="Since 0.8"/>

The maximum number of the [background jobs][3] used for DB compactions.

**Default:** 4

#### `DB_RESOURCE_INDEXER_THREADS` <Badge type="warning" text="Since 0.8"/>

The number threads used for indexing resources. Try 8 or 16 depending on your hardware.

**Default:** 4

#### `DB_RESOURCE_INDEXER_BATCH_SIZE` <Badge type="warning" text="Since 0.8"/> <Badge type="danger" text="Deprecated 0.11"/>

The number of resources which are indexed in a batch. (Deprecated)

**Default:** 1

#### `DB_RESOURCE_STORE_KV_THREADS` <Badge type="warning" text="Since 0.17"/>

The number of threads used for reading and writing resources.

**Default:** 4

#### `DB_KAFKA_BOOTSTRAP_SERVERS` <Badge type="warning" text="Since 0.8"/>

A comma separated list of bootstrap servers for the Kafka transaction log.

**Default:** localhost:9092

#### `DB_KAFKA_MAX_REQUEST_SIZE` <Badge type="warning" text="Since 0.8"/>

The maximum size of a encoded transaction able to send to the Kafka transaction log in bytes.

**Default:** 1048576

#### `DB_KAFKA_COMPRESSION_TYPE` <Badge type="warning" text="Since 0.11"/>

The compression type for transaction data generated by the producer. Valid values are `none`, `gzip`, `snappy`, `lz4`, or `zstd`.

**Default:** snappy

#### `DB_KAFKA_SECURITY_PROTOCOL` <Badge type="warning" text="Since 0.11"/>

Protocol used to communicate with brokers. Valid values are: PLAINTEXT and SSL.

**Default:** PLAINTEXT

#### `DB_KAFKA_SSL_TRUSTSTORE_LOCATION` <Badge type="warning" text="Since 0.11"/>

The location of the trust store file.

#### `DB_KAFKA_SSL_TRUSTSTORE_PASSWORD` <Badge type="warning" text="Since 0.11"/>

The password for the trust store file. If a password is not set, trust store file configured will still be used, but integrity checking is disabled.

#### `DB_KAFKA_SSL_KEYSTORE_LOCATION` <Badge type="warning" text="Since 0.11"/>

The location of the key store file. This is optional for client and can be used for two-way authentication for client.

#### `DB_KAFKA_SSL_KEYSTORE_PASSWORD` <Badge type="warning" text="Since 0.11"/>

The store password for the key store file. This is optional for client and only needed if DB_KAFKA_SSL_KEYSTORE_LOCATION is configured.

#### `DB_KAFKA_SSL_KEY_PASSWORD` <Badge type="warning" text="Since 0.11"/>

The password of the private key in the key store file. This is required for clients only if two-way authentication is configured.

#### `DB_CASSANDRA_CONTACT_POINTS` <Badge type="warning" text="Since 0.8"/>

A comma separated list of contact points for the Cassandra resource store.

**Default:** localhost:9042

#### `DB_CASSANDRA_USERNAME` <Badge type="warning" text="Since 0.11"/>

The username for the Cassandra authentication.

**Default:** cassandra

#### `DB_CASSANDRA_PASSWORD` <Badge type="warning" text="Since 0.11"/>

The password for the Cassandra authentication.

**Default:** cassandra

#### `DB_CASSANDRA_KEY_SPACE` <Badge type="warning" text="Since 0.8"/>

The Cassandra key space were the `resources` table is located.

**Default:** blaze

#### `DB_CASSANDRA_PUT_CONSISTENCY_LEVEL` <Badge type="warning" text="Since 0.8"/>

Cassandra consistency level for resource put (insert) operations. Has to be set to `ONE` on a non-replicated keyspace.

**Default:** TWO

#### `DB_CASSANDRA_REQUEST_TIMEOUT` <Badge type="warning" text="Since 0.11"/>

Timeout in milliseconds for all requests to the Cassandra cluster.

**Default:** 2000

More information about distributed deployment are available [here](distributed-backend.md). 

### Common Environment Variables

#### `PROXY_HOST` <Badge type="warning" text="Since 0.6"/>

REMOVED: use -Dhttp.proxyHost

#### `PROXY_PORT` <Badge type="warning" text="Since 0.6"/>

REMOVED: use -Dhttp.proxyPort

#### `PROXY_USER` <Badge type="warning" text="Since 0.6.1"/>

REMOVED: try [SOCKS Options][1]

#### `PROXY_PASSWORD` <Badge type="warning" text="Since 0.6.1"/>

REMOVED: try [SOCKS Options][1]

#### `CONNECTION_TIMEOUT` <Badge type="warning" text="Since 0.6.3"/>

connection timeout for outbound HTTP requests

#### `REQUEST_TIMEOUT` <Badge type="warning" text="Since 0.6.3"/>

REMOVED

#### `TERM_SERVICE_URI` <Badge type="warning" text="Since 0.6"/> <Badge type="danger" text="Deprecated 0.11"/>

Base URI of the terminology service

**Default:** [http://tx.fhir.org/r4][6]

#### `BASE_URL`

The URL under which Blaze is accessible by clients. The [FHIR API](../api.md) will be accessible under `BASE_URL/CONTEXT_PATH`. Possible `X-Forwarded-Host`, `X-Forwarded-Proto` and `Forwarded` request headers will override this URL.

**Default:** `http://localhost:8080`

#### `CONTEXT_PATH` <Badge type="warning" text="Since 0.11"/>

Context path under which the FHIR RESTful API will be accessible.

**Default:** `/fhir`

#### `SERVER_PORT`

The port of the main HTTP server

**Default:** 8080

#### `METRICS_SERVER_PORT` <Badge type="warning" text="Since 0.6"/>

The port of the Prometheus metrics server

**Default:** 8081

#### `LOG_LEVEL` <Badge type="warning" text="Since 0.6"/>

one of trace, debug, info, warn or error

**Default:** info

#### `JAVA_TOOL_OPTIONS`

| Name                      | Default | Since | Description                                                  |
|:--------------------------|:--------|:------|:-------------------------------------------------------------|
| -&NoBreak;Xmx4g           | -       |       | The maximum amount of heap memory.                           |
| -&NoBreak;Dhttp.proxyHost | -       | v0.11 | The hostname of the proxy server for outbound HTTP requests. |
| -&NoBreak;Dhttp.proxyPort | 80      | v0.11 | The port of the proxy server.                                |

#### `FHIR_OPERATION_EVALUATE_MEASURE_THREADS` <Badge type="warning" text="Since 0.8"/>

The number threads used for [$evaluate-measure](../api/operation/measure-evaluate-measure.md) executions. The default is the number of available processors (CPUs). For measures that do not load lots of resources from disk the default is the right choice. However, if some of the measures load lots of resources directly from disk, it can be beneficial to set the number of threads to 2x or 4x the number of available processors. Be sure to increase `DB_RESOURCE_STORE_KV_THREADS` accordingly to be able to use the increased I/O capabilities.

**Default:** number of CPU cores

#### `FHIR_OPERATION_EVALUATE_MEASURE_TIMEOUT` <Badge type="warning" text="Since 0.19"/>

Timeout in milliseconds for synchronous [$evaluate-measure](../api/operation/measure-evaluate-measure.md) executions. It's recommended to set this as short as possible in order to prevent bad designed CQL queries to impede other CQL queries and the overall performance of the server. 

**Default:** 3600000 (1h)

#### `OPENID_PROVIDER_URL` <Badge type="warning" text="Since 0.11"/>

[OpenID Connect][4] provider URL to enable [authentication][5]

#### `OPENID_CLIENT_TRUST_STORE` <Badge type="warning" text="Since 0.26"/>

A PKCS #12 trust store containing CA certificates needed for the [OpenID Connect][4] provider. The PKCS #12 trust store has to be generated by the Java keytool. OpenSSL will not work.

```sh
keytool -importcert -storetype PKCS12 -keystore "trust-store.p12" \
  -storepass "..." -alias ca -file "cert.pem" -noprompt
```

#### `OPENID_CLIENT_TRUST_STORE_PASS` <Badge type="warning" text="Since 0.26"/>

The password for the PKCS #12 trust store.

#### `ENFORCE_REFERENTIAL_INTEGRITY` <Badge type="warning" text="Since 0.14"/>

Enforce referential integrity on resource create, update and delete. It's enabled by default but can be disabled on proxy/middleware/secondary systems were a primary system ensures referential integrity.

**Default:** true

#### `DB_SYNC_TIMEOUT` <Badge type="warning" text="Since 0.15"/>

Timeout in milliseconds for all reading FHIR interactions acquiring the newest database state. All reading FHIR interactions have to acquire the last database state known at the time the request arrived in order to ensure [consistency](../consistency.md). That database state might not be ready immediately because indexing might be still undergoing. In such a situation, the request has to wait for the database state becoming available. If the database state won't be available before the timeout expires, a 503 Service Unavailable response will be returned. Please increase this timeout if you experience such 503 responses, and you are not able to improve indexing performance or lower transaction load.

**Default:** 10000

#### `DB_SEARCH_PARAM_BUNDLE` <Badge type="warning" text="Since 0.21"/>

Name of a custom search parameter bundle file. Per default, Blaze supports FHIR Search on all FHIR R4 search parameters. However Blaze can be configured to support custom search parameters by specifying the file name of a search parameter bundle in the environment variable `DB_SEARCH_PARAM_BUNDLE`. If such a bundle file name is specified, Blaze will index newly written resources using the search parameters defined in that file. Existing resources can be re-indexed. More information on re-indexing can be found in the [Frontend Docs](../frontend.md).

##### Example Config

```yaml
services:
  blaze:
    image: "samply/blaze:latest"
    environment:
      DB_SEARCH_PARAM_BUNDLE: "/app/custom-search-parameters.json"
    ports:
    - "8080:8080"
    volumes:
    - "custom-search-parameters.json:/app/custom-search-parameters.json:ro"
    - "blaze-data:/app/data"
volumes:
  blaze-data:
```

#### `ENABLE_ADMIN_API` <Badge type="warning" text="Since 0.26"/>

Set to `true` if the optional Admin API should be enabled. Needed by the frontend.

**Default:** `false`

#### `CQL_EXPR_CACHE_SIZE` <Badge type="warning" text="Since 0.28"/>

Size of the CQL expression cache in MiB. This cache is part of the JVM heap. Will be disabled if not given.

#### `CQL_EXPR_CACHE_REFRESH` <Badge type="warning" text="Since 0.28"/>

The duration after which a Bloom filter of the CQL expression cache will be refreshed.

**Default:** PT24H

#### `CQL_EXPR_CACHE_THREADS` <Badge type="warning" text="Since 0.28"/>

The maximum number of parallel Bloom filter calculations for the CQL expression cache.

**Default:** 4

#### `ALLOW_MULTIPLE_DELETE` <Badge type="warning" text="Since 0.30"/>

Allow deleting multiple resources using [Conditional Delete](../api/interaction/delete-type.md).

**Default:** false

#### `ENABLE_INTERACTION_DELETE_HISTORY` <Badge type="warning" text="Since 0.30.1"/>

Enable the [Delete History](../api/interaction/delete-history.md) interaction.

**Default:** `false`

#### `ENABLE_OPERATION_PATIENT_PURGE` <Badge type="warning" text="Since 0.30.1"/>

Enable the [Operation \$purge on Patient](../api/operation/patient-purge.md).

**Default:** `false`

#### `PAGE_STORE_EXPIRE` <Badge type="warning" text="Since 1.0.2"/>

The duration after page store entries expire. Lower that value if the size of the page store, available via the metric `blaze_page_store_estimated_size`, gets to large.

**Default:** PT1H

#### `ENABLE_TERMINOLOGY_SERVICE` <Badge type="warning" text="Since 0.31"/>

Enable the [Terminology Service](../terminology-service.md). This enables terminology operations in [CQL Queries](../cql-queries.md), but it's recommended to separate the terminology server from a data server were CQL queries are run. Please use the env var `EXTERN_TERMINOLOGY_SERVICE_URL` to connect to an external terminology service for data servers. 

**Default:** `false`

#### `TERMINOLOGY_SERVICE_GRAPH_CACHE_SIZE` <Badge type="warning" text="Since 0.32"/>

Number of concepts the graph cache should hold.

**Default:** 100000

#### `ENABLE_TERMINOLOGY_LOINC` <Badge type="warning" text="Since 0.32"/>

Enable LOINC for the Terminology Service by using the value `true`. LOINC doesn't need release files.

**Default:** `false`

#### `ENABLE_TERMINOLOGY_SNOMED_CT` <Badge type="warning" text="Since 0.31"/>

Enable SNOMED CT for the Terminology Service by using the value `true`.

**Default:** `false`

#### `SNOMED_CT_RELEASE_PATH` <Badge type="warning" text="Since 0.31"/>

Path of an official SNOMED CT release.

#### `EXTERN_TERMINOLOGY_SERVICE_URL` <Badge type="warning" text="unreleased"/>

Terminology service URL to make terminology operations available in [CQL Queries](../cql-queries.md).

#### `EXTERN_TERMINOLOGY_SERVICE_CLIENT_TRUST_STORE` <Badge type="warning" text="unreleased"/>

A PKCS #12 trust store containing CA certificates needed for the external terminology service. The PKCS #12 trust store has to be generated by the Java keytool. OpenSSL will not work.

```sh
keytool -importcert -storetype PKCS12 -keystore "trust-store.p12" \
  -storepass "..." -alias ca -file "cert.pem" -noprompt
```

#### `EXTERN_TERMINOLOGY_SERVICE_CLIENT_TRUST_STORE_PASS` <Badge type="warning" text="unreleased"/>

The password for the PKCS #12 trust store.

[1]: <https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/doc-files/net-properties.html#Proxies>
[2]: <https://github.com/facebook/rocksdb/wiki/Setup-Options-and-Basic-Tuning#block-cache-size>
[3]: <https://github.com/facebook/rocksdb/wiki/Thread-Pool>
[4]: <https://openid.net/connect/>
[5]: <../authentication.md>
[6]: <http://tx.fhir.org/r4>
