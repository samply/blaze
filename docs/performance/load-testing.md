# Load Testing

## Systems

The following system was used for the performance evaluation:

| System | Provider | CPU         | Cores |     RAM | SSD                           |
|--------|----------|-------------|------:|--------:|-------------------------------|
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 4 TB Samsung 990 Pro          |
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB | 3.2 TB Intel P5600 over vSAN  |
| LEA79  | on-prem  | EPYC 9555   |   128 | 768 GiB | 12.8 TB Huawei OceanDisk 300P |

All systems were configured according to the [Production Configuration](../production-configuration.md) guide. Deviating from it, all runs on all systems use a `DB_RESOURCE_STORE_KV_THREADS` of 64 instead of the default of 4. That variable sizes the thread pool the resource store uses to read and write resources and so caps how many resources can be written to the resource database at the same time. RocksDB merges concurrent writers into [group commits][3]: the writers waiting at any moment are batched into a single write-ahead log write followed by a single fsync, so the cost of that fsync — which dominates a write on systems with slow syncs — is shared by the whole group. With only four threads, at most four resources can join a group, whereas 64 threads allow much larger groups and so a considerably higher write throughput.

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
BASE=http://localhost:8080/fhir k6 run transaction.js
```

The optional `DURATION` environment variable (default 60 s) sets how long each concurrency level runs.

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

<LineChart src="load-testing/data/transaction-LEA47.csv"
  title="Transaction (LEA47)"
  x-log :x-min="1" :x-max="64" :x-ticks="[1, 2, 4, 8, 16, 32, 64]" />

LEA47 plateaus at about 1390 transactions/s from 16 clients on — nearly three transactions per fsync at its 484 fsyncs/s — with a median processing time of 46 ms and a q99 of 68 ms at 64 clients.

<LineChart src="load-testing/data/transaction-LEA79.csv"
  title="Transaction (LEA79)"
  x-log :x-min="1" :x-max="64" :x-ticks="[1, 2, 4, 8, 16, 32, 64]" />

LEA79 syncs from a write cache in a few microseconds and is not disk-bound at all: it already sustains over 5000 transactions/s at 8 clients and stays there over the whole sweep, with a q99 of 13 ms at 64 clients.

<LineChart src="load-testing/data/transaction-A5N46.csv"
  title="Transaction (A5N46)"
  x-log :x-min="1" :x-max="128" :x-ticks="[1, 2, 4, 8, 16, 32, 64, 128]" />

A5N46 shows the batching most clearly. Its drive syncs at only 178/s, which pins the median processing time at about 30 ms from 4 clients on — but with the batches growing along the concurrency, throughput roughly doubles with every doubling of clients: 234 transactions/s at 8 clients, 1084 at 32 and 2033 at 64 clients, more than eleven transactions per fsync. The q99 stays at about 41 ms over that range.

At 128 clients the doubling stops. Throughput still grows to 3505 transactions/s — more than nineteen transactions per fsync — but the median processing time leaves its 30 ms plateau for the first time and rises to 37 ms. It is also the first level at which the clients outnumber the 64 writer threads of the resource store. The processing times shift up as a block instead of spreading out, with a q99 of only 50 ms, which points at one additional round of waiting rather than at contention.

[1]: <https://k6.io>
[2]: <https://www.hl7.org/fhir/http.html#transaction>
[3]: <https://github.com/facebook/rocksdb/wiki/WAL-Performance>
