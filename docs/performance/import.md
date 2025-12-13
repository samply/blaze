# Import

## Systems

| System | Provider | CPU         | Cores |     RAM |  SSD |
|--------|----------|-------------|------:|--------:|-----:|
| LEA25  | on-prem  | EPYC 7543P  |     4 |  32 GiB | 2 TB |
| LEA36  | on-prem  | EPYC 7543P  |     8 |  64 GiB | 2 TB |
| LEA47  | on-prem  | EPYC 7543P  |    16 | 128 GiB | 2 TB |
| LEA58  | on-prem  | EPYC 7543P  |    32 | 256 GiB | 2 TB |
| LEA79  | on-prem  | EPYC 9555   |   128 | 768 GiB | 2 TB |
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 2 TB |

All systems were configured according to the [Tuning Guide](../tuning-guide.md).

On all systems, the heap memory and the block cache were each configured to use 1/4 of the total available RAM. Consequently, the Blaze process itself uses about half of the available system memory, leaving the remainder for the file system cache.

## Datasets

The following datasets were used:

| Dataset | History  | # Pat. ¹ | # Res. ² | # Obs. ³ | Size on SSD |
|---------|----------|---------:|---------:|---------:|------------:|
| 100k    | 10 years |    100 k |    104 M |     59 M |     202 GiB |
| 100k-fh | full     |    100 k |    317 M |    191 M |     323 GiB |
| 1M      | 10 years |      1 M |   1044 M |    593 M |    1045 GiB |

¹ Number of Patients, ² Total Number of Resources, ³ Number of Observations

The creation of the datasets is described in the [Synthea section](./synthea/README.md). The disc size is measured after full manual compaction of the database. The actual disc size will be up to 50% higher, depending on the state of compaction which happens regularly in the background.

## Import

```sh
blazectl --server http://localhost:8080/fhir upload -c8 <dataset>
```

| System | Dataset | DB Scale Factor | Time (h) | Resources/s |
|--------|---------|----------------:|---------:|------------:|
| LEA47  | 100k    |               1 |    3.048 |        9469 |
| LEA47  | 100k-fh |               1 |    9.797 |        8977 |
| LEA58  | 100k    |               1 |    2.169 |       13305 |
| LEA58  | 100k-fh |               1 |    7.426 |       11843 |
| LEA79  | 1M      |               1 |   18.227 |       15915 |
| LEA79  | 1M      |               4 |   12.534 |       23144 |

Read more about the DB Scale Factor in the [Environment Variables Section](../deployment/environment-variables.md).
