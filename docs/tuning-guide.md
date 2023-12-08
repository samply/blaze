# Tuning Guide

## Recommended System Sizes

The following table lists the recommended system sizes depending on the number of patients. 

| # Patients | Cores |     RAM |    SSD | Heap Mem ¹ | Block Cache ² | Resource Cache ³ |
|-----------:|------:|--------:|-------:|-----------:|--------------:|-----------------:|
|    < 100 k |     4 |  32 GiB | 0.5 TB |      8 GiB |         8 GiB |            2.5 M | 
|      100 k |     8 |  64 GiB |   1 TB |     16 GiB |        16 GiB |              5 M | 
|        1 M |    16 | 128 GiB |   2 TB |     32 GiB |        32 GiB |             10 M | 
|      > 1 M |    32 | 256 GiB |   4 TB |     64 GiB |        64 GiB |             20 M | 

### Configuration

The list of all environment variables can be found in the [Environment Variables Section](deployment/environment-variables.md) under [Deployment](deployment/README.md). The variables important here are:

| Name                   | Use for        | Default | Description                                |
|:-----------------------|----------------|:--------|:-------------------------------------------|
| JAVA_TOOL_OPTIONS      | Heap Mem       | —       | eg. -Xmx8g, -Xmx16g, -Xmx32g or -Xmx64g    |
| DB_BLOCK_CACHE_SIZE    | Block Cache    | 128     | eg. 8192, 16384, 32768 or 65536            |
| DB_RESOURCE_CACHE_SIZE | Resource Cache | 100000  | eg. 2500000, 5000000, 10000000 or 20000000 |

### Performance Metrics

Performance metrics using the systems of recommended sizes can be found for [CQL](performance/cql.md), [FHIR Search](performance/fhir-search.md) and [Import](performance/import.md). 

## Common Considerations

### CPU Cores

Blaze heavily benefits from vertical scaling (number of CPU cores). Even with single clients, transactions and CQL queries scale with the number of CPU cores. Performance tests show that 32 cores can be utilized.

### Memory

Blaze uses three main memory regions, namely the JVM heap memory, the RocksDB block cache, and the OS page cache. The JVM heap memory is used for general operation and specifically for the resource cache which caches resources in their ready to use object form. The RocksDB block cache is separate from the JVM heap memory because it's implemented as off-heap memory region. The block cache stores resources and index segments in uncompressed blob form. After the resource cache, it functions as a second level cache which stores resources in a more compact form and is not subject to JVM garbage collection. The OS page cache functions as a third level of caching, because it contains all recently accessed database files. The database files are compressed and so store data in an even more compact form.

The first recommendation is, to leave half of the available system memory for the OS page cache. For example if you have 16 GB of memory, you should only allocate 8 GB for JVM heap + RocksDB block cache. The ratio between the JVM heap size, and the RocksDB block cache can vary. First you should monitor the JVM heap usage and garbage collection activity in your use case. Blaze provides a Prometheus metrics endpoint on port 8081 per default. Using half of the remaining system memory for the JVM heap and half for the RocksDB block cache will be a good start.

Depending on the capabilities of your disk I/O system, having enough OS page cache for holding most of the active database file size, can really make a huge difference in performance. In case all your database files fit in memory, you have essentially a in-memory database. Tests with up to 128 GB system memory showed that effect.

### Disk I/O

Blaze is designed with local (NVMe) SSD storage in mind. That is the reason why Blaze uses RocksDB as underlying storage engine. RocksDB benefits from fast storage. The idea is that latency of local SSD storage is faster than the network. So remote network storage has no longer an advantage.

If you have only spinning disks available, a bigger OS page cache can help. Otherwise, especially the latency of operations will suffer.

## Transactions

Blaze maintains indexes for FHIR search parameters and CQL evaluation. The indexing process can be executed in parallel if transactions contain more than one resource. Transaction containing only one resource and direct REST interactions like create, update or delete don't benefit from parallel indexing.

Depending on your system, indexing can be I/O or CPU bound. Performance tests show, that at least 20 CPU cores can be utilized for resource indexing.
