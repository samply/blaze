# Operation \$disk-perf <Badge type="info" text="Feature: ADMIN_API"/> <Badge type="warning" text="Since 1.11"/>

The system level \$disk-perf operation measures the performance of the disk volume of one of the database directories with an I/O profile similar to the one Blaze's RocksDB databases produce. It can be used to verify that the storage of a deployment is fast enough before going into production and to diagnose performance problems.

```
POST [base]/$disk-perf
```

The operation is formally described by the OperationDefinition with the canonical URL `https://blaze-server.org/fhir/OperationDefinition/disk-perf`, defined in the Blaze IG.

The benchmark runs three phases against temporary files in the chosen database directory:

| Phase     | Emulates                                              | I/O Pattern                                                                                              |
|-----------|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| seq-write | compactions and memtable flushes writing SST files    | sequential writes in 1 MiB chunks, finishing with an fsync                                                                                            |
| rand-read | point queries reading blocks from SST files           | random reads of `DB_BLOCK_SIZE` (default 16 KiB) blocks, sweeping the reader concurrency in powers of two, bypassing the page cache if possible       |
| fsync     | write-ahead log appends of the tx and resource stores | small sequential writes, each followed by an fsync                                                       |

::: warning
The benchmark competes with regular request processing for disk I/O and writes a test file of the configured size into the database directory. Run it on an otherwise idle server and ensure enough free disk space.
:::

## In Parameters

All parameters are optional.

| Name            | Cardinality | Type        | Documentation                                                                                                 |
|-----------------|-------------|-------------|----------------------------------------------------------------------------------------------------------------|
| database        | 0..1        | code        | The database directory volume to measure: `index` (default), `transaction` or `resource`                      |
| file-size       | 0..1        | decimal     | The size of the test file in GiB, at least 1 MiB and at most 64 GiB. Defaults to 4.                           |
| phase-duration  | 0..1        | decimal     | The duration of each run of the rand-read sweep and the fsync phase in seconds, at most 300. Defaults to 30.  |
| max-concurrency | 0..1        | positiveInt | The maximum number of concurrent reader threads of the rand-read sweep, between 1 and 128. Defaults to 32.    |

## Out Parameters

| Name                                            | Cardinality | Type        | Documentation                                                                     |
|-------------------------------------------------|-------------|-------------|-------------------------------------------------------------------------------------|
| seq-write-throughput                            | 1..1        | Quantity    | The sequential write throughput in bytes per second                               |
| rand-read                                       | 1..*        | parts       | One run of the rand-read sweep per concurrency level                              |
| rand-read.concurrency                           | 1..1        | positiveInt | The number of concurrent reader threads of the run                                |
| rand-read.iops                                  | 1..1        | Quantity    | The number of random read operations per second of the run                        |
| rand-read.throughput                            | 1..1        | Quantity    | The random read throughput of the run in bytes per second                         |
| rand-read.latency-p50 / -p95 / -p99 / -max      | 1..1        | Quantity    | The random read latency percentiles of the run in microseconds                    |
| fsync-rate                                      | 1..1        | Quantity    | The number of write + fsync operations per second                                 |
| fsync-latency-p50 / -p95 / -p99                 | 1..1        | Quantity    | The write + fsync latency percentiles in microseconds                             |
| direct-io                                       | 1..1        | boolean     | Whether the random reads could bypass the page cache                              |
| score                                           | 1..1        | decimal     | The overall score between 0 and 100, where 100 represents a good local NVMe SSD   |
| rating                                          | 1..1        | code        | One of `excellent`, `good`, `acceptable` or `insufficient`, derived from the score |
| processing-duration                             | 1..1        | Quantity    | The duration the whole benchmark took in seconds                                  |

### Response

The response will be always async according the [Asynchronous Interaction Request Pattern][2] from FHIR R5. Polling the async status endpoint of a finished measurement returns a batch-response Bundle whose entry contains the out parameters as Parameters resource. The same results are also available as outputs of the underlying Task resource. See the [Disk I/O Performance][3] documentation for how to interpret them.

### Example

```sh
curl -s -X POST -H 'Content-Type: application/fhir+json' \
  -d '{"resourceType": "Parameters"}' \
  "http://localhost:8080/fhir/\$disk-perf"
```

The `Content-Location` response header contains the URL to poll for the result. A [blazectl][1] subcommand is planned.

The measurement can also be started and monitored in the Jobs section of the admin UI.

[1]: <https://github.com/samply/blazectl>
[2]: <http://hl7.org/fhir/R5/async-bundle.html>
[3]: <../../performance/disk-perf.md>
