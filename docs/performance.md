# Performance

## Transaction Bundle Upload

### Test Data

Generated 10,000 patients with Synthea master branch with Git SHA `4fed9eaf` and standard configuration using `./run_synthea -p 10000` resulting in 9.9 GiB of JSON files.

### Test System

MacBook Pro \(Retina, 15-inch, Mid 2015\) 2,5 GHz Intel Core i7, 16 GB RAM. Blaze version 0.9.0-alpha.26.

### Start Script

```bash
STORAGE=standalone INDEX_DB_DIR=blaze-data/index TRANSACTION_DB_DIR=blaze-data/transaction RESOURCE_DB_DIR=blaze-data/resource DB_RESOURCE_INDEXER_THREADS=8 java -jar blaze-0.9.0-alpha.26-standalone.jar -m blaze.core
```

### Relevant Startup Log Output

```text
Init RocksDB block cache of 128 MB
Init RocksDB statistics
Init resource indexer executor with 8 threads
Open RocksDB key-value store in directory `blaze-data/resource` with options: {:max-background-jobs 4, :compaction-readahead-size 0}. This can take up to several minutes due to forced compaction.
Open key-value store backed resource store.
Create resource cache with a size of 10000 resources
Open RocksDB key-value store in directory `blaze-data/transaction` with options: {:max-background-jobs 4, :compaction-readahead-size 0}. This can take up to several minutes due to forced compaction.
Open local transaction log
Open local database node with a resource indexer batch size of 1
Init FHIR transaction interaction executor with 8 threads
Init server executor with 8 threads
Init JSON parse executor with 8 threads
JVM version: 11.0.7
Maximum available memory: 4096 MiB
Number of available processors: 8
Successfully started Blaze version 0.9.0-alpha.26 in 13.1 seconds
```

### Upload Method

Command line tool `blazectl` with concurrency of 8.

```text
Starting Upload to http://localhost:8080/fhir ...
Uploads          [total, concurrency]     11676, 8
Success          [ratio]                  100.00 %
Duration         [total]                  27m6s
Requ. Latencies  [mean, 50, 95, 99, max]  1.112s, 868ms, 2.312s, 5.786s 24.977s
Proc. Latencies  [mean, 50, 95, 99, max]  1.103s, 863ms, 2.288s, 5.749s 23.992s
Bytes In         [total, mean]            818.08 MiB, 71.75 KiB
Bytes Out        [total, mean]            10.08 GiB, 904.95 KiB
Status Codes     [code:count]             200:11676
```

The upload resulted in the following resource counts:

| Metric | Count |
| :--- | :--- |
| AllergyIntolerance | 5,360 |
| CarePlan | 35,578 |
| Claim | 522,122 |
| Condition | 83,683 |
| DiagnosticReport | 145,727 |
| Encounter | 408,512 |
| ExplanationOfBenefit | 408,512 |
| Goal | 27,125 |
| ImagingStudy | 8,122 |
| Immunization | 144,970 |
| MedicationAdministration | 6,120 |
| MedicationRequest | 113,610 |
| Observation | 2,072,024 |
| Organization | 33,718 |
| Patient | 11,674 |
| Practitioner | 33,714 |
| Procedure | 327,659 |
| **total** | **4,388,230** |

That are 4,388,230 resources in 30 minutes or about 2,400 resources per second on a 8 core machine.

The size of the database directory after the import was 15 GiB and 9 GB after compaction.

### Other Test Systems

On a VM with 32 vCores the same upload with a concurrency of only 8 was 13 minutes and 12 seconds or about 5,500 resources per second. The VM was only utilized about 50% during the import.

