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

| Dataset | System | VUs | Req/s | med | q95 | q99 |
|---------|--------|----:|------:|----:|----:|----:|
| 1M      | A5N46  |   1 |  1330 | 0.5 | 0.7 | 1.1 |
| 1M      | A5N46  |   2 |  2793 | 0.5 | 0.7 | 0.9 |
| 1M      | A5N46  |   4 |  5250 | 0.6 | 0.7 | 1.1 |
| 1M      | A5N46  |   8 |  9749 | 0.6 | 0.8 | 1.3 |
| 1M      | A5N46  |  16 | 17012 | 0.7 | 1.0 | 1.7 |
| 1M      | A5N46  |  32 | 24035 | 1.0 | 1.9 | 3.4 |

## Patient Everything

### Results

| Dataset | System | VUs | Req/s |  med |   q95 |   q99 |
|---------|--------|----:|------:|-----:|------:|------:|
| 1M      | A5N46  |   1 | 40.50 | 15.7 |  28.3 |  54.7 |
| 1M      | A5N46  |   2 | 73.07 | 16.9 |  31.7 |  41.7 |
| 1M      | A5N46  |   4 | 126.6 | 19.9 |  39.6 |  61.3 |
| 1M      | A5N46  |   8 | 192.4 | 26.7 |  56.2 |  87.3 |
| 1M      | A5N46  |  16 | 240.9 | 46.1 | 101.6 | 127.5 |
| 1M      | A5N46  |  32 | 272.2 | 91.8 | 166.6 | 200.9 |

[1]: <https://k6.io>
