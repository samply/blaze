# Import

## Systems

The following systems, with increasing resources, were used for the performance evaluation:

| System | Provider | CPU        | Cores |     RAM |  SSD | Heap Mem | Block Cache | Resource Cache ¹ | Background Jobs ² | Indexer Threads ³ |
|--------|----------|------------|------:|--------:|-----:|---------:|------------:|-----------------:|------------------:|------------------:|
| LEA47  | on-prem  | EPYC 7543P |    16 | 128 GiB | 2 TB |   32 GiB |      32 GiB |             10 M |                 8 |                16 | 
| LEA58  | on-prem  | EPYC 7543P |    32 | 256 GiB | 2 TB |   64 GiB |      64 GiB |             20 M |                16 |                32 | 

¹ Size of the resource cache (DB_RESOURCE_CACHE_SIZE)
² The maximum number of the [background jobs][3] used for DB compactions (DB_MAX_BACKGROUND_JOBS)
³ The number threads used for indexing resources (DB_RESOURCE_INDEXER_THREADS)

## Datasets

The following datasets were used:

| Dataset | History  | # Pat. ¹ | # Res. ² | # Obs. ³ | Disc Size |
|---------|----------|---------:|---------:|---------:|----------:|
| 100k    | 10 years |    100 k |    104 M |     59 M |   202 GiB |
| 100k-fh | full     |    100 k |    317 M |    191 M |   323 GiB |
| 1M      | 10 years |      1 M |   1044 M |    593 M |  1045 GiB |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

The creation of the datasets is described in the [Synthea section](./synthea/README.md). The disc size is measured after full manual compaction of the database and can be higher during the import.

## Import

```sh
blazectl --server http://localhost:8080/fhir upload -c8 <dataset>
```

| System | Dataset | Time (h) | Resources/s |
|--------|---------|---------:|------------:|
| LEA47  | 100k    |    3,048 |        9469 |
| LEA47  | 100k-fh |    9,797 |        8977 |
| LEA58  | 100k    |    2,169 |       13305 |
| LEA58  | 100k-fh |    7,426 |       11843 |
