# Disk I/O Performance

Blaze is designed for local NVMe SSD storage. RocksDB, the storage engine of Blaze, exploits the fast random access patterns SSDs provide. This page describes how to measure whether the disks of a deployment are fast enough.

## Built-In Measurement <Badge type="info" text="Feature: ADMIN_API"/> <Badge type="warning" text="Since 1.11"/>

Blaze contains a disk performance measurement job that benchmarks a database directory volume with an I/O profile similar to the one Blaze's RocksDB databases produce. It can be started either in the Jobs section of the admin UI or via the [\$disk-perf operation](../api/operation/disk-perf.md).

The benchmark runs three phases against temporary files in the chosen database directory:

* **seq-write** — writes a test file (default 4 GiB) sequentially in 1 MiB chunks, like RocksDB writes SST files during compactions and memtable flushes,
* **rand-read** — reads blocks of `DB_BLOCK_SIZE` (default 16 KiB) at random offsets, like RocksDB reads blocks on point queries. The number of concurrent readers is swept in powers of two from 1 up to `max-concurrency` (default 32, so 1, 2, 4, 8, 16, 32), with one run of the configured phase duration per level. The resulting distribution of IOPS over the concurrency tells the story of a disk: the run with one reader exposes the pure device (or network) latency, the slope shows how the storage absorbs parallelism and the plateau shows saturation — matching Blaze, which reads at different concurrencies in different scenarios. Direct I/O is used to bypass the page cache where the filesystem supports it, so the numbers reflect the disk instead of the memory,
* **fsync** — writes small chunks sequentially, each followed by an fsync, like the write-ahead logs of the transaction and resource stores which are synced on every write.

::: warning
The benchmark competes with regular request processing for disk I/O and writes a test file of the configured size into the database directory. Run it on an otherwise idle server and ensure enough free disk space.
:::

### Results

The job outputs the raw numbers of each phase:

| Output                                       | Description                                                                    |
|----------------------------------------------|--------------------------------------------------------------------------------|
| seq-write-throughput                         | sequential write throughput in bytes per second                               |
| read-iops                                    | random read operations per second, one output per concurrency level           |
| read-throughput                              | random read throughput in bytes per second, one output per concurrency level  |
| read-latency-p50 / -p95 / -p99 / -max        | random read latency percentiles in microseconds, one output per concurrency level |
| fsync-rate                                   | write + fsync operations per second                                           |
| fsync-latency-p50 / -p95 / -p99              | write + fsync latency percentiles in microseconds                             |
| direct-io                                    | whether the random reads could bypass the page cache                          |

Each per-level read output carries the number of concurrent readers of its run in the `https://blaze-server.org/fhir/StructureDefinition/disk-perf-concurrency` extension. The admin UI plots the IOPS over the concurrency together with the reference curve of a good local NVMe SSD.

### Score

In addition to the raw numbers, the job outputs a score between 0 and 100. The random read sub-score compares the IOPS of every run of the concurrency sweep against the reference curve of a good local NVMe SSD — 10,000 IOPS per reader, corresponding to a 100 µs read, capped at 320,000 IOPS at 32 readers — and combines the per-level results as a geometric mean with equal weights, so both the low-concurrency latency regime and the high-concurrency throughput regime count. Sequential writes are normalized against 1 GB/s and fsyncs against 1,000 per second. The three sub-scores are combined as a weighted geometric mean with random reads weighted at one half and sequential writes and fsyncs at one quarter each, because random reads dominate Blaze's interactive query load. The geometric mean ensures that one collapsed dimension collapses the whole score.

Because every level of the sweep contributes to the score, scores are only comparable between runs with the same `max-concurrency`.

| Score  | Rating       | Interpretation                                                          |
|--------|--------------|-------------------------------------------------------------------------|
| ≥ 80   | excellent    | performs like a good local NVMe SSD                                     |
| ≥ 50   | good         | well suited for production use                                          |
| ≥ 25   | acceptable   | works, but larger deployments will be limited by disk I/O               |
| < 25   | insufficient | expect poor performance; consider local NVMe SSD storage                |

If `direct-io` is false, the filesystem doesn't support bypassing the page cache, and the random read numbers are inflated by page cache hits — treat the score as an upper bound in that case.

### Example Results

The measurement was run on the following systems, which are also used in the [CQL](cql.md) and [FHIR Search](fhir-search.md) performance evaluations:

| System | Provider | CPU         | Cores |     RAM | SSD                         |
|--------|----------|-------------|------:|--------:|-----------------------------|
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB | 2 TB Intel P5600 over vSAN  |
| LEA79  | on-prem  | EPYC 9555   |   128 | 768 GiB | 2 TB Huawei OceanDisk 300P  |
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 2 TB Samsung 990 Pro        |

All systems were configured according to the [Production Configuration](../production-configuration.md) guide.

::: info
The read values below were measured with a fixed concurrency of 8 readers, before the benchmark swept the concurrency. A current run reports them for every level of the sweep.
:::

| Output               |      LEA47 |     LEA79 |     A5N46 |
|----------------------|-----------:|----------:|----------:|
| seq-write-throughput |   471 MB/s | 3.62 GB/s | 3.47 GB/s |
| read-iops            |   18.4 k/s | 114.6 k/s | 153.7 k/s |
| read-throughput      |   302 MB/s | 1.88 GB/s | 2.52 GB/s |
| read-latency-p50     |   440.9 µs |   65.5 µs |   46.3 µs |
| read-latency-p95     |   571.7 µs |   77.9 µs |   71.5 µs |
| read-latency-p99     |   679.9 µs |   85.0 µs |   85.0 µs |
| read-latency-max     | 21757.4 µs |  240.4 µs | 2287.0 µs |
| fsync-rate           |   463.8 /s | 123.1 k/s |  171.1 /s |
| fsync-latency-p50    |  2097.2 µs |    7.5 µs | 5439.3 µs |
| fsync-latency-p95    |  2493.9 µs |    8.2 µs | 7692.4 µs |
| fsync-latency-p99    |  3234.3 µs |   11.6 µs | 8388.6 µs |
| direct-io            |       true |      true |      true |
| score                |       41.5 |     100.0 |      64.3 |
| rating               | acceptable | excellent |      good |

All three systems support direct I/O, so the random read numbers reflect the disks themselves. LEA79 reaches the maximum score with all three dimensions well above the reference values — its fsync rate of over 100 k/s indicates a write cache that can acknowledge syncs almost immediately. A5N46 shows even faster random reads, but its low fsync rate of 171/s limits the score to good. LEA47 stays acceptable because both random reads and sequential writes fall well below the reference values — the Intel P5600 itself is a fast NVMe SSD, but it is accessed over vSAN, which adds network latency to every I/O operation.
