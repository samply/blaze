# Load Testing

## Systems

The following systems with rising resources were used for performance evaluation:

| System | Provider | CPU         | Cores |     RAM |  SSD | Heap Mem ¹ | Block Cache ² | Resource Cache ³ |
|--------|----------|-------------|------:|--------:|-----:|-----------:|--------------:|-----------------:|
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 2 TB |     24 GiB |        24 GiB |              5 M |

## Methods

The load testing tool [k6][1] is used to create load from another host in the same network as the test system. The test system starts with an empty database.

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

[1]: <https://k6.io>
