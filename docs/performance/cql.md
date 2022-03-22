# CQL Performance

## TL;DR

TODO

## Test for one Condition

In this section, we count the patients having one condition. The CQL library is the following:

```text
library Retrieve
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem sct: 'http://snomed.info/sct'

context Patient

define InInitialPopulation:
  exists [Condition: Code '...' from sct]
```

| CPU Model   | # CPU's | RAM (GB) | Heap Mem (GB) | Block Cache (GB) | # Patients | Code | # Hits | Time (s) |
|-------------|--------:|---------:|--------------:|-----------------:|-----------:|------|-------:|---------:|
| E5-2687W v4 |       8 |      128 |             4 |                1 |      100 k |      |    1 k |     0.65 |
| E5-2687W v4 |       8 |      128 |             4 |                1 |      100 k |      |   10 k |      0.7 |
| E5-2687W v4 |       8 |      128 |             4 |                1 |      100 k |      |   64 k |      0.8 |
| E5-2687W v4 |       8 |      128 |             4 |                1 |        1 M |      |  9.6 k |      7.9 |
| E5-2687W v4 |       8 |      128 |             4 |                1 |        1 M |      |   98 k |      7.8 |
| E5-2687W v4 |       8 |      128 |             4 |                1 |        1 M |      |  643 k |      7.9 |
| E5-2687W v4 |       8 |      128 |             4 |               32 |        1 M |      |   98 k |      3.3 |
| E5-2687W v4 |       8 |      128 |             4 |               32 |        1 M |      |  643 k |      3.6 |
