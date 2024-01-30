## 50 Rare Code Search

### Data

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |   15 k |     5.64 |  0.030 |  17.7 k |
| 100k    | LEA36  |   15 k |     3.31 |  0.050 |  30.2 k |
| 100k    | LEA47  |   15 k |     2.03 |  0.023 |  49.2 k |
| 100k    | LEA58  |   15 k |     1.37 |  0.013 |  72.8 k |
| 100k-fh | LEA58  |   16 k |     6.84 |  0.018 |  14.6 k |
| 1M      | LEA47  |  155 k |     8.56 |  0.014 | 116.8 k |
| 1M      | LEA58  |  155 k |    13.01 |  0.053 |  76.8 k |

### CQL Query

```sh
cql/search.sh condition-50-rare
```

## All Code Search

### Data

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |   99 k |     2.51 |  0.015 |  39.8 k |
| 100k    | LEA36  |   99 k |     1.55 |  0.018 |  64.5 k |
| 100k    | LEA47  |   99 k |     0.93 |  0.021 | 107.8 k |
| 100k    | LEA58  |   99 k |     0.63 |  0.009 | 159.1 k |
| 100k-fh | LEA58  |  100 k |     1.55 |  0.006 |  64.7 k |
| 1M      | LEA47  |  995 k |     4.75 |  0.014 | 210.5 k |
| 1M      | LEA58  |  995 k |     6.05 |  0.017 | 165.4 k |

### CQL Query

```sh
cql/search.sh condition-all
```

## Inpatient Stress Search

### Data

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |    2 k |     4.73 |  0.032 |  21.1 k |
| 100k    | LEA36  |    2 k |     2.97 |  0.029 |  33.7 k |
| 100k    | LEA47  |    2 k |     1.59 |  0.008 |  63.0 k |
| 100k    | LEA58  |    2 k |     1.27 |  0.023 |  78.8 k |
| 100k-fh | LEA58  |    2 k |     4.41 |  0.041 |  22.7 k |
| 1M      | LEA58  |   16 k |    11.08 |  0.044 |  90.2 k |

### CQL Query

```sh
cql/search.sh inpatient-stress
```
