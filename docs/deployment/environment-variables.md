# Environment Variables

Blaze is configured solely through environment variables. There is a default for every variable. So all are optional.

The following table contains all of them:

| Name | Default | Since | Description |
| :--- | :--- | :--- | :--- |
| DB\_DIR | – | v0.8 | The directory were the database files are stored. This directory must not exist on the first start of Blaze and will be created by Blaze. However the parent directory has to exist. The default is to use an in-memory, volatile database. |
| DB\_BLOCK\_CACHE\_SIZE | 128 | v0.8 | The size of the [block cache](https://github.com/facebook/rocksdb/wiki/Setup-Options-and-Basic-Tuning#block-cache-size) of the DB in MB. |
| DB\_RESOURCE\_CACHE\_SIZE | 10000 | v0.8 | The size of the resource cache of the DB in number of resources. |
| DB\_MAX\_BACKGROUND\_JOBS | 4 | v0.8 | The maximum number of the [background jobs](https://github.com/facebook/rocksdb/wiki/RocksDB-Basics#multi-threaded-compactions) used for DB compactions. |
| DB\_RESOURCE\_INDEXER\_THREADS | 4 | v0.8 | The number threads used for indexing resources. |
| DB\_RESOURCE\_INDEXER\_BATCH\_SIZE | 1 | v0.8 | The number of resources which are indexed in a batch. |
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

