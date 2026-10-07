# Load Testing

## Systems

The following system was used for the performance evaluation:

| System | Provider | CPU         | Cores |     RAM | SSD                           |
|--------|----------|-------------|------:|--------:|-------------------------------|
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 4 TB Samsung 990 Pro          |
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB | 3.2 TB Intel P5600 over vSAN  |
| LEA79  | on-prem  | EPYC 9555   |   128 | 768 GiB | 12.8 TB Huawei OceanDisk 300P |

All systems were configured according to the [Production Configuration](../production-configuration.md) guide. Deviating from it, all runs on all systems use a `DB_RESOURCE_STORE_KV_THREADS` of 128 instead of the default of 4, matching the highest concurrency level of the sweep. That variable sizes the thread pool the resource store uses to read and write resources. Every transaction hands all its resources to that pool as one unit, so the pool size caps how many transactions can write to the resource database at the same time. RocksDB merges concurrent writers into [group commits][3]: the writers waiting at any moment are batched into a single write-ahead log write followed by a single fsync, so the cost of that fsync — which dominates a write on systems with slow syncs — is shared by the whole group. With only four threads, at most four transactions can join a group, whereas 128 threads allow much larger groups and so a considerably higher write throughput. Sizing the pool to the concurrency also keeps it from becoming a queue of its own; see [Resource Store Write Concurrency](../production-configuration.md#resource-store-write-concurrency).

For the same reason, all systems use a `DB_RESOURCE_INDEXER_THREADS` of 128 instead of the default of 4. That variable sizes the thread pool that indexes the resources of the transactions, one resource per thread at a time. With the small transactions of this test, the default goes unnoticed on disks with slow syncs, because the disk limits the throughput long before the indexer does. On LEA79, however, four threads cap the transaction load test at about 8,000 transactions/s from 8 clients on, less than half of what the system reaches with 128 threads; see [Resource Indexer Concurrency](../production-configuration.md#resource-indexer-concurrency).

In addition, the JVM heap of all systems is fixed at its maximum size by setting `-Xms` to the value of `-Xmx`. Left to size the heap itself, the JVM starts with a small one and can shrink it again during a run. On LEA79, such a shrink in the middle of the sweep was followed by a burst of garbage collection pauses that more than doubled the q99 at 64 clients.

## Datasets

The following datasets were used:

| Dataset | History  | # Pat. ¹ | # Res. ² | # Obs. ³ | Size on SSD |
|---------|----------|---------:|---------:|---------:|------------:|
| 1M      | 10 years |      1 M |   1044 M |    593 M |    1045 GiB |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

## Methods

The load testing tool [k6][1] is used to create load from another host in the same network as the test system.

Each test is a k6 script in the `load-testing` directory and is run via the `Makefile`. The FHIR base URL of the system under test — for example a Blaze running in a Docker container — is passed via the `BASE` environment variable:

```sh
BASE=http://localhost:8080/fhir k6 run patient-everything.js
```

The optional `DURATION` environment variable (default 60 s) sets how long each concurrency level runs.

The transaction test reads the version of the system under test from its CapabilityStatement and writes it into the `version` column of its results, so that each result file carries the version it was measured against.

Single runs of the transaction test vary by about 5 % in throughput, so the `Makefile` runs it three times per system, keeps each run in `load-testing/data/raw` and publishes the per-level median of the runs. Every run needs an empty server, so for this test k6 runs on the system under test itself, where Blaze is started via the `docker-compose-<system>.yml` of that system. In front of every run, the `reset.sh` script shuts Blaze down, deletes the contents of the host directory the Compose file mounts as data volume, starts Blaze again and waits for it to become healthy:

```sh
BASE=http://localhost:8080/fhir make data/transaction-LEA47.csv
```

The transaction test sweeps over the concurrency levels 1 to 128, one after the other. The optional `VUS` environment variable restricts it to a single level, which is what to use while profiling the server: the batches of the transaction log and the resource store grow with the number of concurrent clients, so every level has its own ratio of per-batch to per-entry work that a profile over the whole sweep would blur. Such a run reports its result line on stdout instead of writing the CSV, so it leaves the committed sweep data alone:

```sh
BASE=http://localhost:8080/fhir VUS=64 DURATION=300 k6 run transaction.js
```

## Single Patient Reads

### Results

| Dataset | System | VUs | Req/s |  med |  q95 |  q99 |
|---------|--------|----:|------:|-----:|-----:|-----:|
| 1M      | A5N46  |   1 |  1405 | 0.50 | 0.74 | 1.47 |
| 1M      | A5N46  |   2 |  3907 | 0.45 | 0.57 | 0.67 |
| 1M      | A5N46  |   4 |  7248 | 0.53 | 0.59 | 0.69 |
| 1M      | A5N46  |   8 | 13381 | 0.55 | 0.67 | 0.88 |
| 1M      | A5N46  |  16 | 23678 | 0.60 | 0.82 | 1.21 |
| 1M      | A5N46  |  32 | 38314 | 0.73 | 1.13 | 1.90 |
| 1M      | A5N46  |  48 | 45679 | 0.89 | 1.58 | 3.22 |
| 1M      | A5N46  |  64 | 48868 | 1.07 | 2.20 | 4.12 |

## Patient Everything

### Results

| Dataset | System | VUs | Req/s |   med |   q95 |   q99 |
|---------|--------|----:|------:|------:|------:|------:|
| 1M      | A5N46  |   1 | 40.50 |  15.7 |  28.3 |  54.7 |
| 1M      | A5N46  |   2 | 73.07 |  16.9 |  31.7 |  41.7 |
| 1M      | A5N46  |   4 | 162.9 |  21.9 |  42.3 |  59.0 |
| 1M      | A5N46  |   8 | 234.9 |  30.5 |  60.1 |  90.2 |
| 1M      | A5N46  |  16 | 261.2 |  57.6 |  98.7 | 125.1 |
| 1M      | A5N46  |  32 | 258.7 | 119.4 | 174.3 | 202.1 |

## Transaction

This write test measures the throughput and latency of small [FHIR transactions][2].

The [`transaction.js`](load-testing/transaction.js) script repeatedly `POST`s a small transaction bundle to the FHIR base URL. Each bundle creates one Patient and one Observation, where the Observation references the Patient via a bundle-internal URN, so reference resolution is exercised as well. The Patient's birthDate and the Observation's systolic blood pressure are randomized per transaction, so the date and quantity search-param indices see a realistic spread of values instead of a single repeated entry. New resources are created on every request, so the database grows over the course of the run.

All runs start from an empty database and grow it with every request, so there is no fixed dataset. The test was run on three systems to show how strongly transaction throughput depends on disk performance (see [Disk Performance](disk-perf.md)): LEA79 stores its data on a local NVMe disk with an fsync latency of a few microseconds, LEA47 accesses its disk over vSAN with an fsync latency of about 2 ms, and A5N46 uses a local consumer NVMe SSD that acknowledges a sync only once the data has reached the flash, at about 5 ms.

### Results

Every transaction durably writes its transaction log entry and its resources before it is answered, so the disk's sync behaviour sets the processing time. It does not cap the throughput, though: the transaction log batches all transactions submitted while the previous write was in flight into a single write followed by one fsync, and the resource store group-commits its concurrent writers the same way. The batches grow with the number of concurrent clients, so throughput scales past the disk's raw fsync rate.

All values are the per-level medians of three runs against a development build of Blaze at commit `5773c58b`.

<LineChart src="load-testing/data/transaction-LEA47.csv"
  title="Transaction (LEA47)"
  x-log :x-min="1" :x-max="128" :x-ticks="[1, 2, 4, 8, 16, 32, 64, 128]" />

LEA47 scales to about 2630 transactions/s at 32 clients — more than five transactions per fsync at its 484 fsyncs/s — and on to 3327 at 64 clients, but gains little beyond that with 3621 at 128 clients, about seven and a half transactions per fsync. The median processing time roughly doubles at each of the last two levels instead, from 11 ms at 32 clients to 18 ms at 64 and 34 ms at 128, with a q99 of 74 ms at 128 clients. With the throughput staying put while the concurrency doubles, the clients added beyond 64 only queue.

<LineChart src="load-testing/data/transaction-LEA79.csv"
  title="Transaction (LEA79)"
  x-log :x-min="1" :x-max="128" :x-ticks="[1, 2, 4, 8, 16, 32, 64, 128]" />

LEA79 syncs from a write cache in a few microseconds and is not disk-bound at all: throughput scales to about 12,800 transactions/s at 16 clients and grows more slowly from there, to about 16,900 at 64 and 19,600 at 128 clients, with a median processing time of 6.1 ms and a q99 of 8.8 ms at 128 clients.

<LineChart src="load-testing/data/transaction-A5N46.csv"
  title="Transaction (A5N46)"
  x-log :x-min="1" :x-max="128" :x-ticks="[1, 2, 4, 8, 16, 32, 64, 128]" />

A5N46 shows the batching most clearly. Its drive syncs at only 178/s, which pins the median processing time at about 29 ms from 4 clients on — but with the batches growing along the concurrency, throughput roughly doubles with every doubling of clients: 247 transactions/s at 8 clients, 1035 at 32, 2038 at 64 and 4073 at 128 clients, nearly twenty-three transactions per fsync.

The processing time never leaves that plateau: the median stays there and the q99 at about 41 ms at every level up to 128 clients, so each client waits for one fsync no matter how many others share it. That is what makes the doubling possible in the first place, and it holds only as long as the resource store has a free thread for every client — which is why these runs size its pool to the highest concurrency level of the sweep.

[1]: <https://k6.io>
[2]: <https://www.hl7.org/fhir/http.html#transaction>
[3]: <https://github.com/facebook/rocksdb/wiki/WAL-Performance>
