# Manual Deployment

The installation works under Windows, Linux and macOS. The only dependency is an installed OpenJDK 11. Blaze is tested with [AdoptOpenJDK][1].

Blaze runs on the JVM and comes as single JAR file. Download the most recent version [here](https://github.com/samply/blaze/releases/tag/v0.11.0-alpha.4). Look for `blaze-0.11.0-alpha.4-standalone.jar`.

After the download, you can start blaze with the following command (Linux, macOS):

```bash
java -jar blaze-0.11.0-alpha.4-standalone.jar -m blaze.core
```

Blaze will run with an in-memory, volatile database for testing and demo purposes.

Blaze can be run with durable storage by setting the environment variables `STORAGE` to `standalone`. 

Under Linux/macOS:

```bash
STORAGE=standalone java -jar blaze-0.11.0-alpha.4-standalone.jar -m blaze.core
```

Under Windows, you need to set the Environment variables in the PowerShell before starting Blaze:

```powershell
$Env:STORAGE="standalone"
java -jar blaze-0.11.0-alpha.4-standalone.jar -m blaze.core
```

This will create three directories called `index`, `transaction` and `resource` inside the current working directory, one for each database part used.

The output should look like this:

```text
2020-06-23 06:44:41.000+0000 my-server INFO [blaze.system:157] - Set log level to: info
2020-06-23 06:44:41.403+0000 my-server INFO [blaze.system:41] - Try to read blaze.edn ...
2020-06-23 06:44:41.439+0000 my-server INFO [blaze.system:147] - Feature RocksDB Key-Value Store enabled
2020-06-23 06:44:41.440+0000 my-server INFO [blaze.system:147] - Feature In-Memory, Volatile Key-Value Store disabled
2020-06-23 06:44:41.441+0000 my-server INFO [blaze.system:147] - Feature OpenID Authentication disabled
2020-06-23 06:44:41.477+0000 my-server INFO [blaze.system:72] - Loading namespaces ...
2020-06-23 06:45:03.709+0000 my-server INFO [blaze.system:74] - Loaded the following namespaces: blaze.db.indexer.resource, blaze.db.search-param-registry, blaze.db.indexer, blaze.db.kv.rocksdb, blaze.db.kv, blaze.db.resource-cache, blaze.db.tx-log, blaze.db.tx-log.local, blaze.db.node, blaze.fhir.operation.evaluate-measure, blaze.handler.health, blaze.interaction.history.instance, blaze.interaction.history.system, blaze.interaction.history.type, blaze.interaction.transaction, blaze.interaction.create, blaze.interaction.delete, blaze.interaction.read, blaze.interaction.search-compartment, blaze.interaction.search-type, blaze.interaction.update, blaze.structure-definition, blaze.rest-api, blaze.handler.app, blaze.metrics, blaze.handler.metrics, blaze.server, blaze.thread-pool-executor-collector, blaze.db.indexer.tx
2020-06-23 06:45:03.807+0000 my-server INFO [blaze.db.indexer.resource:121] - Init resource indexer executor with 4 threads
2020-06-23 06:45:03.810+0000 my-server INFO [blaze.db.kv.rocksdb:317] - Init RocksDB block cache of 128 MB
2020-06-23 06:45:03.933+0000 my-server INFO [blaze.db.kv.rocksdb:360] - Init RocksDB statistics
2020-06-23 06:45:03.936+0000 my-server INFO [blaze.db.kv.rocksdb:343] - Open RocksDB key-value store in directory `db` with options: {:max-background-jobs 4, :compaction-readahead-size 0}
2020-06-23 06:45:04.032+0000 my-server INFO [blaze.db.search-param-registry:189] - Init in-memory fixed R4 search parameter registry
2020-06-23 06:45:04.585+0000 my-server INFO [blaze.db.indexer.resource:111] - Init resource indexer
2020-06-23 06:45:04.587+0000 my-server INFO [blaze.db.indexer.tx:253] - Init transaction indexer
2020-06-23 06:45:04.590+0000 my-server INFO [blaze.db.resource-cache:49] - Create resource cache with a size of 10000 resources
2020-06-23 06:45:04.625+0000 my-server INFO [blaze.db.tx-log.local:217] - Open local transaction log with a resource indexer batch size of 1.
2020-06-23 06:45:04.633+0000 my-server INFO [blaze.db.node:92] - Open local database node
2020-06-23 06:45:04.634+0000 my-server INFO [blaze.fhir.operation.evaluate-measure:50] - Init FHIR $evaluate-measure operation executor with 4 threads
2020-06-23 06:45:04.637+0000 my-server INFO [blaze.fhir.operation.evaluate-measure:39] - Init FHIR $evaluate-measure operation handler
2020-06-23 06:45:04.640+0000 my-server INFO [blaze.handler.health:21] - Init health handler
2020-06-23 06:45:04.642+0000 my-server INFO [blaze.interaction.history.instance:87] - Init FHIR history instance interaction handler
2020-06-23 06:45:04.643+0000 my-server INFO [blaze.interaction.history.system:83] - Init FHIR history system interaction handler
2020-06-23 06:45:04.645+0000 my-server INFO [blaze.interaction.history.type:79] - Init FHIR history type interaction handler
2020-06-23 06:45:04.646+0000 my-server INFO [blaze.interaction.transaction:384] - Init FHIR transaction interaction executor with 4 threads
2020-06-23 06:45:04.648+0000 my-server INFO [blaze.interaction.transaction:373] - Init FHIR transaction interaction handler
2020-06-23 06:45:04.650+0000 my-server INFO [blaze.interaction.create:72] - Init FHIR create interaction handler
2020-06-23 06:45:04.651+0000 my-server INFO [blaze.interaction.delete:62] - Init FHIR delete interaction handler
2020-06-23 06:45:04.653+0000 my-server INFO [blaze.interaction.read:89] - Init FHIR read interaction handler
2020-06-23 06:45:04.655+0000 my-server INFO [blaze.interaction.search-compartment:124] - Init FHIR search-compartment interaction handler
2020-06-23 06:45:04.658+0000 my-server INFO [blaze.interaction.search-type:158] - Init FHIR search-type interaction handler
2020-06-23 06:45:04.660+0000 my-server INFO [blaze.interaction.update:127] - Init FHIR update interaction handler
2020-06-23 06:45:04.663+0000 my-server INFO [blaze.rest-api:405] - Init JSON parse executor with 4 threads
2020-06-23 06:45:05.106+0000 my-server INFO [blaze.structure-definition:190] - Read structure definitions resulting in: 190 structure definitions
2020-06-23 06:45:05.305+0000 my-server INFO [blaze.rest-api:432] - Init FHIR RESTful API with base URL: http://localhost:8080/fhir
2020-06-23 06:45:06.022+0000 my-server INFO [blaze.handler.app:35] - Init app handler
2020-06-23 06:45:06.027+0000 my-server INFO [blaze.system:196] - Init server executor with 4 threads
2020-06-23 06:45:06.030+0000 my-server INFO [blaze.thread-pool-executor-collector:70] - Init thread pool executor collector.
2020-06-23 06:45:06.036+0000 my-server INFO [blaze.metrics:26] - Init metrics registry
2020-06-23 06:45:06.082+0000 my-server INFO [blaze.handler.metrics:29] - Init metrics handler
2020-06-23 06:45:06.083+0000 my-server INFO [blaze.system:217] - Start metrics server on port 8081
2020-06-23 06:45:06.848+0000 my-server INFO [blaze.system:205] - Start main server on port 8080
2020-06-23 06:45:06.858+0000 my-server INFO [blaze.core:60] - JVM version: 11.0.7
2020-06-23 06:45:06.859+0000 my-server INFO [blaze.core:61] - Maximum available memory: 4070 MiB
2020-06-23 06:45:06.859+0000 my-server INFO [blaze.core:62] - Number of available processors: 4
2020-06-23 06:45:06.860+0000 my-server INFO [blaze.core:63] - Successfully started Blaze version 0.11.0-alpha.4 in 25,5 seconds
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
  "version": "0.11.0-alpha.4"
}
```

Blaze will be configured through environment variables which are documented [here][2].

[1]: <https://adoptopenjdk.net>
[2]: <environment-variables.md>
