# Import

## Systems

The following systems were used for performance evaluation:

| System | Provider | CPU        | Cores |     RAM |  SSD | Heap Mem | Block Cache | Resource Cache ¹ | Background Jobs ² | Indexer Threads ³ |
|--------|----------|------------|------:|--------:|-----:|---------:|------------:|-----------------:|------------------:|------------------:|
| LEA47  | on-prem  | EPYC 7543P |    16 | 128 GiB | 2 TB |   32 GiB |      32 GiB |             10 M |                 8 |                16 | 
| LEA58  | on-prem  | EPYC 7543P |    32 | 256 GiB | 2 TB |   64 GiB |      64 GiB |             20 M |                16 |                32 | 

¹ Size of the resource cache (DB_RESOURCE_CACHE_SIZE)
² The maximum number of the [background jobs][3] used for DB compactions (DB_MAX_BACKGROUND_JOBS)
³ The number threads used for indexing resources (DB_RESOURCE_INDEXER_THREADS)

## Datasets

The following datasets were used:

| Dataset | # Pat. ¹ | # Res. ² | # Obs. ³ |
|---------|---------:|---------:|---------:|
| 100k    |    100 k |    104 M |     59 M |
| 100k-fh |    100 k |    317 M |    191 M |
| 1M      |      1 M |   1044 M |    593 M |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

## Import

```sh
blazectl --server http://localhost:8080/fhir upload -c8 data/synthea/data-100k-full-history/
```

| System | Dataset | Time (s) | Bytes In (GiB) | Resources/s |
|--------|---------|---------:|---------------:|------------:|
| LEA58  | 100k-fh |    28366 |          53.94 |       11161 |

```
Uploads          [total, concurrency]     100000, 8
Success          [ratio]                  100.00 %
Duration         [total]                  7h52m46s
Requ. Latencies  [mean, 50, 95, 99, max]  2.267s, 1.518s, 6.796s, 11.772s 40.552s
Proc. Latencies  [mean, 50, 95, 99, max]  2.267s, 1.518s, 6.796s, 11.772s 40.552s
Bytes In         [total, mean]            53.94 GiB, 565.57 KiB
Bytes Out        [total, mean]            592.38 GiB, 6.07 MiB
Status Codes     [code:count]             200:100000
```
