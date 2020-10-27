# Docker Deployment

Blaze comes as a web application which needs one Docker volume to store its data.

## Volume

```bash
docker volume create blaze-data
```

## Blaze

```bash
docker run -d --name blaze -p 8080:8080 -v blaze-data:/app/data samply/blaze:0.8.0
```

Blaze should log something like this:

```text
20-06-23 08:06:24 73c1e4c03d80 INFO [blaze.system:157] - Set log level to: info
2020-06-23 08:06:24.373+0000 73c1e4c03d80 INFO [blaze.system:41] - Try to read blaze.edn ...
2020-06-23 08:06:24.412+0000 73c1e4c03d80 INFO [blaze.system:147] - Feature RocksDB Key-Value Store enabled
2020-06-23 08:06:24.412+0000 73c1e4c03d80 INFO [blaze.system:147] - Feature In-Memory, Volatile Key-Value Store disabled
2020-06-23 08:06:24.413+0000 73c1e4c03d80 INFO [blaze.system:147] - Feature OpenID Authentication disabled
2020-06-23 08:06:24.430+0000 73c1e4c03d80 INFO [blaze.system:72] - Loading namespaces ...
2020-06-23 08:06:47.346+0000 73c1e4c03d80 INFO [blaze.system:74] - Loaded the following namespaces: blaze.db.indexer.resource, blaze.db.search-param-registry, blaze.db.indexer, blaze.db.kv.rocksdb, blaze.db.kv, blaze.db.resource-cache, blaze.db.tx-log, blaze.db.tx-log.local, blaze.db.node, blaze.fhir.operation.evaluate-measure, blaze.handler.health, blaze.interaction.history.instance, blaze.interaction.history.system, blaze.interaction.history.type, blaze.interaction.transaction, blaze.interaction.create, blaze.interaction.delete, blaze.interaction.read, blaze.interaction.search-compartment, blaze.interaction.search-type, blaze.interaction.update, blaze.structure-definition, blaze.rest-api, blaze.handler.app, blaze.metrics, blaze.handler.metrics, blaze.server, blaze.thread-pool-executor-collector, blaze.db.indexer.tx
2020-06-23 08:06:47.392+0000 73c1e4c03d80 INFO [blaze.db.indexer.resource:121] - Init resource indexer executor with 4 threads
2020-06-23 08:06:47.395+0000 73c1e4c03d80 INFO [blaze.db.kv.rocksdb:317] - Init RocksDB block cache of 128 MB
2020-06-23 08:06:47.478+0000 73c1e4c03d80 INFO [blaze.db.kv.rocksdb:360] - Init RocksDB statistics
2020-06-23 08:06:47.482+0000 73c1e4c03d80 INFO [blaze.db.kv.rocksdb:343] - Open RocksDB key-value store in directory `/app/data/db` with options: {:max-background-jobs 4, :compaction-readahead-size 0}
2020-06-23 08:06:47.591+0000 73c1e4c03d80 INFO [blaze.db.search-param-registry:189] - Init in-memory fixed R4 search parameter registry
2020-06-23 08:06:47.969+0000 73c1e4c03d80 INFO [blaze.db.indexer.resource:111] - Init resource indexer
2020-06-23 08:06:47.971+0000 73c1e4c03d80 INFO [blaze.db.indexer.tx:253] - Init transaction indexer
2020-06-23 08:06:47.974+0000 73c1e4c03d80 INFO [blaze.db.resource-cache:49] - Create resource cache with a size of 10000 resources
2020-06-23 08:06:48.003+0000 73c1e4c03d80 INFO [blaze.db.tx-log.local:217] - Open local transaction log with a resource indexer batch size of 1.
2020-06-23 08:06:48.011+0000 73c1e4c03d80 INFO [blaze.db.node:92] - Open local database node
2020-06-23 08:06:48.012+0000 73c1e4c03d80 INFO [blaze.fhir.operation.evaluate-measure:50] - Init FHIR $evaluate-measure operation executor with 4 threads
2020-06-23 08:06:48.015+0000 73c1e4c03d80 INFO [blaze.fhir.operation.evaluate-measure:39] - Init FHIR $evaluate-measure operation handler
2020-06-23 08:06:48.019+0000 73c1e4c03d80 INFO [blaze.handler.health:21] - Init health handler
2020-06-23 08:06:48.020+0000 73c1e4c03d80 INFO [blaze.interaction.history.instance:87] - Init FHIR history instance interaction handler
2020-06-23 08:06:48.022+0000 73c1e4c03d80 INFO [blaze.interaction.history.system:83] - Init FHIR history system interaction handler
2020-06-23 08:06:48.024+0000 73c1e4c03d80 INFO [blaze.interaction.history.type:79] - Init FHIR history type interaction handler
2020-06-23 08:06:48.025+0000 73c1e4c03d80 INFO [blaze.interaction.transaction:384] - Init FHIR transaction interaction executor with 4 threads
2020-06-23 08:06:48.027+0000 73c1e4c03d80 INFO [blaze.interaction.transaction:373] - Init FHIR transaction interaction handler
2020-06-23 08:06:48.029+0000 73c1e4c03d80 INFO [blaze.interaction.create:72] - Init FHIR create interaction handler
2020-06-23 08:06:48.031+0000 73c1e4c03d80 INFO [blaze.interaction.delete:62] - Init FHIR delete interaction handler
2020-06-23 08:06:48.033+0000 73c1e4c03d80 INFO [blaze.interaction.read:89] - Init FHIR read interaction handler
2020-06-23 08:06:48.035+0000 73c1e4c03d80 INFO [blaze.interaction.search-compartment:124] - Init FHIR search-compartment interaction handler
2020-06-23 08:06:48.037+0000 73c1e4c03d80 INFO [blaze.interaction.search-type:158] - Init FHIR search-type interaction handler
2020-06-23 08:06:48.038+0000 73c1e4c03d80 INFO [blaze.interaction.update:127] - Init FHIR update interaction handler
2020-06-23 08:06:48.040+0000 73c1e4c03d80 INFO [blaze.rest-api:405] - Init JSON parse executor with 4 threads
2020-06-23 08:06:48.374+0000 73c1e4c03d80 INFO [blaze.structure-definition:190] - Read structure definitions resulting in: 190 structure definitions
2020-06-23 08:06:48.434+0000 73c1e4c03d80 INFO [blaze.rest-api:432] - Init FHIR RESTful API with base URL: http://localhost:8080/fhir
2020-06-23 08:06:48.984+0000 73c1e4c03d80 INFO [blaze.handler.app:35] - Init app handler
2020-06-23 08:06:48.990+0000 73c1e4c03d80 INFO [blaze.system:196] - Init server executor with 4 threads
2020-06-23 08:06:48.992+0000 73c1e4c03d80 INFO [blaze.thread-pool-executor-collector:70] - Init thread pool executor collector.
2020-06-23 08:06:48.997+0000 73c1e4c03d80 INFO [blaze.metrics:26] - Init metrics registry
2020-06-23 08:06:49.045+0000 73c1e4c03d80 INFO [blaze.handler.metrics:29] - Init metrics handler
2020-06-23 08:06:49.047+0000 73c1e4c03d80 INFO [blaze.system:217] - Start metrics server on port 8081
2020-06-23 08:06:49.223+0000 73c1e4c03d80 INFO [blaze.system:205] - Start main server on port 8080
2020-06-23 08:06:49.235+0000 73c1e4c03d80 INFO [blaze.core:60] - JVM version: 11.0.6
2020-06-23 08:06:49.236+0000 73c1e4c03d80 INFO [blaze.core:61] - Maximum available memory: 1488 MiB
2020-06-23 08:06:49.236+0000 73c1e4c03d80 INFO [blaze.core:62] - Number of available processors: 4
2020-06-23 08:06:49.238+0000 73c1e4c03d80 INFO [blaze.core:63] - Successfully started Blaze version 0.8.0 in 24.9 seconds
```

In order to test connectivity, query the health endpoint:

```bash
curl http://localhost:8080/health
```

After that please note that the [FHIR RESTful API](https://www.hl7.org/fhir/http.html) is available under `http://localhost:8080/fhir`. A good start is to query the [CapabilityStatement](https://www.hl7.org/fhir/capabilitystatement.html) of Blaze using [jq](https://stedolan.github.io/jq/) to select only the software key of the JSON output:

```bash
curl -H 'Accept:application/fhir+json' -s http://localhost:8080/fhir/metadata | jq .software
```

that should return:

```javascript
{
  "name": "Blaze",
  "version": "0.8.0"
}
```

Blaze will be configured through environment variables which are documented [here](environment-variables.md).

## Docker Compose

A Docker Compose file looks like this:

```text
version: '3.2'
services:
  blaze:
    image: "samply/blaze:0.8.0"
    environment:
      BASE_URL: "http://localhost:8080"
      JAVA_TOOL_OPTIONS: "-Xmx2g"
    ports:
    - "8080:8080"
    volumes:
    - "blaze-data:/app/data"
volumes:
  blaze-data:
```

