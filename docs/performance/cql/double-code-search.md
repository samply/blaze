## Double Code Search

In this section, CQL Queries for selecting patients which have Condition resources with one of two codes used.

![](cql/double-code-search-100k.png)

### Data

| Dataset | System | # Hits | Time (s) | StdDev |  Pat./s |
|---------|--------|-------:|---------:|-------:|--------:|
| 100k    | LEA25  |    9 k |     0.35 |  0.008 | 283.7 k |
| 100k    | LEA36  |    9 k |     0.24 |  0.004 | 417.9 k |
| 100k    | LEA47  |    9 k |     0.15 |  0.002 | 665.2 k |
| 100k    | LEA58  |    9 k |     0.12 |  0.003 | 857.9 k |
| 100k-fh | LEA58  |    9 k |     0.40 |  0.002 | 248.5 k |
| 1M      | LEA36  |   87 k |     6.17 |  0.059 | 162.0 k |
| 1M      | LEA47  |   87 k |     1.09 |  0.005 | 918.4 k |
| 1M      | LEA58  |   87 k |     1.13 |  0.004 | 881.8 k |

### CQL Query

```text
library "condition-two"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'
code fever: '386661006' from sct
code cough: '49727002' from sct

context Patient

define InInitialPopulation:
  exists [Condition: fever] or 
  exists [Condition: cough]
```

```sh
cql/search.sh condition-two
```
