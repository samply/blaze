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

In addition to the raw numbers, the job outputs a score between 0 and 100. The random read sub-score compares the IOPS of every run of the concurrency sweep against the reference curve of a good local NVMe SSD — 10,000 IOPS per reader, corresponding to a 100 µs read, capped at 320,000 IOPS at 32 readers — and combines the per-level results as a geometric mean with equal weights, so both the low-concurrency latency regime and the high-concurrency throughput regime count. Sequential writes are normalized against 1 GB/s (10⁹ bytes per second, or 954 MiB/s in the binary units the measured throughputs are given in) and fsyncs against 1,000 per second. The three sub-scores are combined as a weighted geometric mean with random reads weighted at one half and sequential writes and fsyncs at one quarter each, because random reads dominate Blaze's interactive query load. The geometric mean ensures that one collapsed dimension collapses the whole score.

Because every level of the sweep contributes to the score, scores are only comparable between runs with the same `max-concurrency`.

| Score  | Rating       | Interpretation                                                          |
|--------|--------------|-------------------------------------------------------------------------|
| ≥ 80   | excellent    | performs like a good local NVMe SSD                                     |
| ≥ 50   | good         | well suited for production use                                          |
| ≥ 25   | acceptable   | works, but larger deployments will be limited by disk I/O               |
| < 25   | insufficient | expect poor performance; consider local NVMe SSD storage                |

If `direct-io` is false, the filesystem doesn't support bypassing the page cache, and the random read numbers are inflated by page cache hits — treat the score as an upper bound in that case.

### Example Results

The measurement was run on the following systems, which are also used in the [CQL](cql.md), [FHIR Search](fhir-search.md) and [Load Testing](load-testing.md) performance evaluations:

| System | Provider | CPU         | Cores |     RAM | SSD                           |
|--------|----------|-------------|------:|--------:|-------------------------------|
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 4 TB Samsung 990 Pro          |
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB | 3.2 TB Intel P5600 over vSAN  |
| LEA79  | on-prem  | EPYC 9555   |   128 | 768 GiB | 12.8 TB Huawei OceanDisk 300P |

All systems were configured according to the [Production Configuration](../production-configuration.md) guide. Every measurement ran on an otherwise idle server with the default parameters: a 4 GiB test file, 30 s per phase and a maximum concurrency of 32.

#### A5N46

<DiskPerfStats src="disk-perf/A5N46.json" />

<DiskPerfChart src="disk-perf/A5N46.json" title="Random Read IOPS (A5N46)" />

<DiskPerfChart src="disk-perf/A5N46.json" metric="latency"
  title="Random Read Latency (A5N46)" />

A5N46 has the fastest random reads of the three: a single reader reaches 18.8 k IOPS at a median latency of 55 µs, and the sweep scales almost linearly to 386 k IOPS at 32 readers, staying above the reference curve at every level. That last part is by construction: the reference curve is modelled on this very drive, set deliberately below its measured numbers so that other good local NVMe SSDs clear it as well. Its fsync rate of 178/s at a median fsync latency of 5.4 ms is by far the weakest of the three, though — the drive acknowledges a sync only once the data has reached the flash instead of from a write cache. Because every transaction has to sync its write-ahead log entries, that is what limits the [transaction load test](load-testing.md#transaction) on this system to between 90 and 160 transactions/s despite the fast reads.

#### LEA47

<DiskPerfStats src="disk-perf/LEA47.json" />

<DiskPerfChart src="disk-perf/LEA47.json" title="Random Read IOPS (LEA47)" />

<DiskPerfChart src="disk-perf/LEA47.json" metric="latency"
  title="Random Read Latency (LEA47)" />

The Intel P5600 itself is a fast NVMe SSD, but LEA47 accesses it over vSAN, which adds network latency to every I/O operation: a single reader reaches only 2.2 k IOPS at a median latency of 441 µs, less than a quarter of the reference. The IOPS still scale linearly with the concurrency while the latency stays flat over the whole sweep, so it is the round-trip, not the drive, that the reads wait for. Together with 460 MiB/s of sequential write throughput and 484 fsyncs/s at a median latency of 1.9 ms, that ends at an acceptable score of 33.5 — the system works, but larger deployments will be limited by disk I/O.

#### LEA79

<DiskPerfStats src="disk-perf/LEA79.json" />

<DiskPerfChart src="disk-perf/LEA79.json" title="Random Read IOPS (LEA79)" />

<DiskPerfChart src="disk-perf/LEA79.json" metric="latency"
  title="Random Read Latency (LEA79)" />

LEA79 reaches the maximum score with all three dimensions above the reference values. The random reads scale linearly to 440 k IOPS at 32 readers, above the reference of 320 k, at a median latency that stays between 65 µs and 72 µs over the whole sweep. Sequential writes reach 10.7 GiB/s, and the fsync rate of 122 k/s at a median latency of 8.2 µs indicates a write cache that can acknowledge syncs almost immediately, which is why the same [transaction load test](load-testing.md#transaction) sustains over 5000 transactions/s here.

All three systems support direct I/O, so the random read numbers reflect the disks themselves.
