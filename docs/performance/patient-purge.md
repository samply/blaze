# Patient Purge Performance

## TL;DR

On LEA79, Blaze purges a Patient with about 20,000 resources in 0.036 seconds, which is about 560,000 resources per second, and in 0.046 seconds if each resource has two versions. A Patient with 110 resources takes 0.007 seconds.

## Systems

The following systems were used for the performance evaluation:

| System | Provider | CPU         | Cores |     RAM | SSD                           |
|--------|----------|-------------|------:|--------:|-------------------------------|
| LEA79  | on-prem  | EPYC 9555   |   128 | 512 GiB | 12.8 TB Huawei OceanDisk 300P |
| A5N46  | on-prem  | Ryzen 9900X |    24 |  96 GiB | 4 TB Samsung 990 Pro          |

The systems were configured as described in the [FHIR Search Performance](./fhir-search.md#systems) evaluation.

## Dataset

The 1M dataset described in the [FHIR Search Performance](./fhir-search.md#datasets) evaluation was used.

## Purging Patients

This section evaluates the performance of the [`$purge`](../api/operation/patient-purge.md) operation on Patients. Each purge removes a Patient together with all resources selected by the [purge GraphDefinition](../api/operation/patient-purge.md#selected-resources). Referential integrity was enforced, which is the default, so each purge also checks that no resource that isn't purged references one of the purged resources.

### Script

The script `patient-purge.sh` is used. It runs four tests, which purge the candidate Patients listed in the script, starting with the largest one:

* `patient-purge-large` purges the Patients of the 1M dataset with the most resources, which have between 15,000 and 22,000 resources.
* `patient-purge-small` purges 1000 of the Patients of the 1M dataset with the fewest resources, which have between 12 and 110 resources.
* `patient-purge-large-updated` purges the same kind of Patients as `patient-purge-large`, but updates all their resources before, so that each resource has two versions.
* `patient-purge-small-updated` does the same for the Patients of `patient-purge-small`.

The resources of the 1M dataset have only one version each. The updated tests find the resources of each Patient with the [`$graph`](../api/operation/graph.md) operation and the purge GraphDefinition and update all of them by setting their language. They update the resources of all Patients they purge before the first purge, four Patients at a time, and restart Blaze afterwards, so that neither the updates nor their caching are measured. So Blaze has to have the `$graph` operation enabled with [ENABLE_OPERATION_GRAPH](../deployment/environment-variables.md#enable-operation-graph), in addition to the `$purge` operation.

The first purges of each test are a warmup that isn't taken into account. Their number is set with `WARMUPS`, which defaults to 5, and the number of purges after the warmup with `REPEATS`, which defaults to 10.

Because each purge removes another Patient, the database has to be reset to its state before the first test, so that a repeated run purges the same Patients. A test purges the first candidate Patients that still exist. So without a reset between the tests, the updated tests purge the Patients following the ones the tests before them purged.

The script takes the Docker Compose file of the Blaze under test as first argument and the base URL of Blaze as optional second argument, which defaults to `http://localhost:8080/fhir`. An executable that resets the database can be given as optional third argument:

```sh
docs/performance/patient-purge.sh docker-compose.yml http://localhost:8080/fhir ./reset-database.sh
```

The script runs it before each test while Blaze is stopped and starts Blaze afterwards. So every test starts with the same database, and the updated tests purge the same Patients as the tests without updates.

A quick way to reset the database is a reflink copy of an untouched original. On filesystems that support reflinks, like XFS, `cp -a --reflink=always` creates a copy whose files share the data blocks of the original files instead of duplicating them. So the copy takes only seconds, whatever the size of the database. A shared block is only copied when one of the files overwrites it, and RocksDB never overwrites data in its files: it writes new files and appends to them. So writing to the copy costs as much as writing to any other file. A reset script that replaces the data directory of Blaze, here `/data/blaze`, with a reflink copy of the original in `/data/blaze-original` looks like this:

```sh
#!/bin/bash
set -euo pipefail

sudo rm -rf /data/blaze
sudo cp -a --reflink=always /data/blaze-original /data/blaze
```

Snapshots of the whole volume make writing more expensive instead. A classic LVM snapshot, created with `lvcreate --snapshot` from a regular logical volume, has to record every first write to a part of the volume. On A5N46, that made a small write followed by an fsync about four times slower, 20 ms instead of 5 ms, and the purges of 32 large Patients with two versions took 0.10 seconds on average instead of 0.06 seconds on a reflink copy.

### Results

| System | Dataset | Patients | Versions ¹ | # Res. ² | Time (s) | StdDev | Res/s ³ |
|--------|---------|----------|-----------:|---------:|---------:|-------:|--------:|
| LEA79  | 1M      | large    |          1 |     20 k |    0.036 |  0.006 | 559.7 k |
| LEA79  | 1M      | large    |          2 |     20 k |    0.046 |  0.006 | 445.8 k |
| LEA79  | 1M      | small    |          1 |      110 |    0.007 |  0.001 |  16.0 k |
| LEA79  | 1M      | small    |          2 |      110 |    0.008 |  0.001 |  14.6 k |
| A5N46  | 1M      | large    |          1 |     20 k |    0.039 |  0.004 | 515.3 k |
| A5N46  | 1M      | large    |          2 |     20 k |    0.051 |  0.006 | 399.1 k |
| A5N46  | 1M      | small    |          1 |      110 |    0.012 |  0.001 |   9.5 k |
| A5N46  | 1M      | small    |          2 |      110 |    0.012 |  0.001 |   9.4 k |

¹ number of versions of each resource, ² average number of resources purged per Patient, ³ resources per second

The values are averages over the 10 purges after the 5 warmup purges, which are the defaults. On both systems, the database was on XFS and the reset script replaced it with a reflink copy of the original before each test.
