---
aside: false
---

<style>
.text-emerald {
  color: #10b981;
}
.text-blue {
  color: #3b82f6;
}
.text-purple {
  color: #8b5cf6;
}
</style>

# Production Configuration Guide

> [!important]
> This guide is essential reading for deploying Blaze in production environments.

## Recommended System Sizes

The following tables lists the minimum recommended system resources based on patient count, assuming approximately 1,000 resources per patient. Each configuration should be dedicated to a single Blaze instance. Avoid running other memory-intensive processes on the same system.

| # Patients | Cores |     RAM |    SSD | Heap Mem | Block Cache | Resource Cache | DB Scale Factor | CQL Cache |
|-----------:|------:|--------:|-------:|---------:|------------:|---------------:|----------------:|----------:|
|       10 k |     2 |   8 GiB | 100 GB |    2 GiB |       2 GiB |         0.25 M |               1 |   128 MiB | 
|     < 50 k |     4 |  16 GiB | 250 GB |    4 GiB |       4 GiB |          0.5 M |               1 |   128 MiB | 
|    < 100 k |     4 |  32 GiB | 500 GB |    8 GiB |       8 GiB |         1.25 M |               2 |   512 MiB | 
|      100 k |     8 |  64 GiB |   1 TB |   16 GiB |      16 GiB |          2.5 M |               2 |   512 MiB | 
|        1 M |    16 | 128 GiB |   2 TB |   32 GiB |      32 GiB |            5 M |               4 |     1 GiB | 
|      > 1 M |    32 | 256 GiB |   4 TB |   64 GiB |      64 GiB |           10 M |               4 |     1 GiB | 

### Memory Allocation Strategy

In general, available RAM should be distributed as follows:
* <span class="text-emerald">**25%** → JVM heap memory</span>
* <span class="text-blue">**25%** → RocksDB block cache</span>
* <span class="text-purple">**50%** → OS page cache (for RocksDB database file access)</span>

The resource cache is configured by the number of resources instead of the amount of memory. The resource numbers given assume a certain resource size taken from [Synthea][1] resources. For fine-tuning that number, the Metric `JVM Memory Used by Pool` should be used.

> [!important]
> Leave half of the available system memory "free" for the OS page cache  
> <small>See [System Memory](#memory)</small>

### DB Scale Factor

Sizes of DB in-memory buffers (memtables), SST files, and internal data structures are scaled by the `DB_SCALE_FACTOR` environment variable. This affects key RocksDB parameters including write buffer size, target file size, and maximum bytes per level.

The default value of `1` is appropriate for small databases with less than 50k patients or 50M resources in total. For larger databases, you should increase this factor.

**Impact of Higher Scale Factors:**
* **Memory**: Increases memory usage outside the JVM heap
* **Disk**: Creates larger individual SST files
* **File Count**: Results in fewer total files in large databases

**When to Increase:**
Consider increasing the scale factor when you encounter:
* Database size exceeding 50k patients or 50M resources
* File descriptor (`ulimit`) problems due to too many SST files
* Compaction performance issues with many small files

### Configuration

The list of all environment variables can be found in the [Environment Variables Section](deployment/environment-variables.md) under [Deployment](deployment.md). The variables important here are:

| Name                           | Use for                 | Default | Examples                                              |
|:-------------------------------|-------------------------|:--------|:------------------------------------------------------|
| `JAVA_TOOL_OPTIONS`            | Heap Mem                | —       | -Xmx2g, -Xmx4g, -Xmx8g, -Xmx16g, -Xmx32g or -Xmx64g   |
| `DB_BLOCK_CACHE_SIZE`          | Block Cache             | 128     | 2048, 4096, 8192, 16384, 32768 or 65536 (in megabyte) |
| `DB_RESOURCE_CACHE_SIZE`       | Resource Cache          | 100000  | 250000, 500000, 1250000, 2500000, 5000000 or 10000000 |
| `DB_SCALE_FACTOR`              | DB Buffers/File Sizes   | 1       | 1, 2, 4, 8 or 16                                      |
| `CQL_EXPR_CACHE_SIZE`          | CQL Expression Cache    | —       | 128, 512, 1024 (in megabyte)                          |
| `DB_RESOURCE_STORE_KV_THREADS` | Read-/Writing Resources | 4       | 4, 8, 16 or 32                                        |

## Performance Benchmarks

Review performance metrics for properly configured systems:

* [CQL Query Performance](performance/cql.md)
* [FHIR Search Performance](performance/fhir-search.md)
* [Import Performance](performance/import.md)

## Architecture Considerations

### CPU Cores

Blaze performance scales directly with CPU core count (vertical scaling). Transaction and CQL query processing is parallelized even for single-client workloads, meaning more cores translate to better performance. Testing has confirmed efficient utilization of systems with up to 64 cores.

### Memory

Blaze uses three main memory regions, namely the JVM heap memory, the RocksDB block cache, and the OS page cache. 

* The <span class="text-emerald">JVM heap memory</span> is used for general operation and specifically for the resource cache which caches resources in their ready to use object form. 
* The <span class="text-blue">RocksDB block cache</span> is separate from the JVM heap memory because it's implemented as an off-heap memory region. The block cache stores resources and index segments in uncompressed blob form. After the resource cache, it functions as a second-level cache which stores resources in a more compact form and is not subject to JVM garbage collection. 
* The <span class="text-purple">OS page cache</span> functions as a third level of caching because it contains all recently accessed database files. The database files are compressed and so store data in an even more compact form.

#### Heap and Block Cache Size

The split between JVM heap and RocksDB block cache depends on your workload. Start by monitoring JVM heap usage and garbage collection through Blaze's Prometheus metrics endpoint (available on port 8081 by default). As a starting point, divide the allocated RAM equally: 50% for the JVM heap and 50% for the RocksDB block cache.

#### OS Page Cache

We recommend reserving half of your system memory for the OS page cache. For instance, on a 16 GB system, allocate only 8 GB total between the JVM heap and RocksDB block cache, leaving the other 8 GB for the OS.

> [!tip]
> This "cache" memory might appear unused in typical monitoring tools, which might lead administrators to think it can be reclaimed or reallocated. However, it should not be reduced or allocated to other applications.

The operating system’s page cache can significantly improve performance, particularly on systems with limited disk I/O throughput. If your cache can hold the most actively accessed database files, you'll see significant gains. When all database files fit in memory, the system effectively operates as an in-memory database.

### Storage Requirements

> [!tip]
> Local NVMe SSD storage is recommended.

Blaze's architecture is optimized for the low-latency characteristics of local solid-state storage. RocksDB, the underlying storage engine, exploits the fast random access patterns that SSDs provide. Since local NVMe latency is typically lower than network round-trip time, network-attached storage offers no architectural advantage.

Legacy Storage: If only spinning disks are available, maximize the OS page cache allocation to compensate. However, expect higher latency, particularly for interactive operations.

## Transactions

Blaze maintains indexes for FHIR search parameters and CQL evaluation. The indexing process can be executed in parallel if transactions contain more than one resource. Transactions containing only one resource and direct REST interactions like create/update/delete don't benefit from parallel indexing.

Depending on your system, indexing can be I/O or CPU-bound. Performance tests show that at least 64 CPU cores can be utilized for resource indexing.

[1]: <https://github.com/synthetichealth/synthea>
[2]: <https://github.com/facebook/rocksdb/wiki/Setup-Options-and-Basic-Tuning#block-cache-size>