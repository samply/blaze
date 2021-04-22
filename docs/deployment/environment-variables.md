# Environment Variables

Blaze is configured solely through environment variables. There is a default for every variable. So all variables are optional. 

A part of the environment variables depends on the storage variant chosen. The storage variant can be set through the `STORAGE` env var. The default is `in-memory` for the JAR and `standalone` for the Docker image. The third setting is `distributed`. The following tables list the database relevant environment variables by storage variant.

### In-memory

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| DB\_RESOURCE\_INDEXER\_BATCH\_SIZE | 1 | v0.8 | The number of resources which are indexed in a batch. |

### Standalone

The three database directories must not exist on the first start of Blaze and will be created by Blaze itself. It's possible to put this three directories on different disks in order to improve performance.

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| INDEX\_DB\_DIR | index \* | v0.8 | The directory were the index database files are stored.  |
| TRANSACTION\_DB\_DIR | transaction \* | v0.8 | The directory were the transaction log files are stored. This directory must not exist on the first start of Blaze and will be created by Blaze. |
| RESOURCE\_DB\_DIR | resource \* | v0.8 | The directory were the resource files are stored. This directory must not exist on the first start of Blaze and will be created by 
| DB\_RESOURCE\_INDEXER\_BATCH\_SIZE | 1 | v0.8 | The number of resources which are indexed in a batch. |
| DB\_BLOCK\_CACHE\_SIZE | 128 | v0.8 | The size of the [block cache][9] of the DB in MB. |
| DB\_RESOURCE\_CACHE\_SIZE | 100000 | v0.8 | The size of the resource cache of the DB in number of resources. |
| DB\_MAX\_BACKGROUND\_JOBS | 4 | v0.8 | The maximum number of the [background jobs][10] used for DB compactions. |
| DB\_RESOURCE\_INDEXER\_THREADS | 4 | v0.8 | The number threads used for indexing resources. |
| DB\_RESOURCE\_INDEXER\_BATCH\_SIZE | 1 | v0.8 | The number of resources which are indexed in a batch. |
 
\* In the JAR variant. The Docker image uses a directory below the `/app/data` directory.

### Distributed

The distributed storage variant only uses the index database locally. 

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| INDEX\_DB\_DIR | index \* | v0.8 | The directory were the index database files are stored.  |
| DB\_BLOCK\_CACHE\_SIZE | 128 | v0.8 | The size of the [block cache][9] of the DB in MB. |
| DB\_RESOURCE\_CACHE\_SIZE | 100000 | v0.8 | The size of the resource cache of the DB in number of resources. |
| DB\_MAX\_BACKGROUND\_JOBS | 4 | v0.8 | The maximum number of the [background jobs][10] used for DB compactions. |
| DB\_RESOURCE\_INDEXER\_THREADS | 4 | v0.8 | The number threads used for indexing resources. |
| DB\_RESOURCE\_INDEXER\_BATCH\_SIZE | 1 | v0.8 | The number of resources which are indexed in a batch. |
| DB\_KAFKA\_BOOTSTRAP\_SERVERS | localhost:9092 | v0.8 | A comma separated list of bootstrap servers for the Kafka transaction log. |
| DB\_KAFKA\_MAX\_REQUEST\_SIZE | 1048576 | v0.8 | The maximum size of a encoded transaction able to send to the Kafka transaction log in bytes. |
| DB\_CASSANDRA\_CONTACT\_POINTS | localhost:9042 | v0.8 | A comma separated list of contact points for the Cassandra resource store. |
| DB\_CASSANDRA\_KEY\_SPACE | blaze | v0.8 | The Cassandra key space were the `resources` table is located. |
| DB\_CASSANDRA\_PUT\_CONSISTENCY\_LEVEL | TWO | v0.8 | Cassandra consistency level for resource put (insert) operations. Has to be set to `ONE` on a non-replicated keyspace. |

\* In the JAR variant. The Docker image uses a directory below the `/app/data` directory.

More information about distributed deployment are available [here](distributed.md). 

### Other Environment Variables

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| PROXY\_HOST | — | v0.6 | REMOVED: use -Dhttp.proxyHost |
| PROXY\_PORT | — | v0.6 | REMOVED: use -Dhttp.proxyPort |
| PROXY\_USER | — | v0.6.1 | REMOVED: try [SOCKS Options][1] |
| PROXY\_PASSWORD | — | v0.6.1 | REMOVED: try [SOCKS Options][1] |
| CONNECTION\_TIMEOUT | 5 s | v0.6.3 | connection timeout for outbound HTTP requests |
| REQUEST\_TIMEOUT | 30 s | v0.6.3 | REMOVED |
| TERM\_SERVICE\_URI | [http://tx.fhir.org/r4](http://tx.fhir.org/r4) | v0.6 | Base URI of the terminology service |
| BASE\_URL | [http://localhost:8080](http://localhost:8080) |  | The URL under which Blaze is accessible by clients. The [FHIR RESTful API](https://www.hl7.org/fhir/http.html) will be accessible under `BASE_URL/fhir`. |
| SERVER\_PORT | 8080 |  | The port of the main HTTP server |
| METRICS\_SERVER\_PORT | 8081 | v0.6 | The port of the Prometheus metrics server |
| LOG\_LEVEL | info | v0.6 | one of trace, debug, info, warn or error |
| JAVA\_TOOL\_OPTIONS | — |  | JVM options \(Docker only\) |
| FHIR\_OPERATION\_EVALUATE\_MEASURE\_THREADS | 4 | v0.8 | The maximum number of parallel $evaluate-measure executions. Not the same as the number of threads used for measure evaluation which equal to the number of available processors. |
| OPENID\_PROVIDER\_URL | - | v0.11 | [OpenID Connect][2] provider URL to enable [authentication][3] |

### Common JAVA_TOOL_OPTIONS

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| -Xmx4g | - | | The maximum amount of heap memory. |
| -Dhttp.proxyHost | - | v0.11 | The hostname of the proxy server for outbound HTTP requests. |
| -Dhttp.proxyPort | 80 | v0.11 | The port of the proxy server. |

[1]: <https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/doc-files/net-properties.html#Proxies>
[2]: <https://openid.net/connect/>
[3]: <../authentication.md>
