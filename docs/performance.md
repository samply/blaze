# Performance

## Transaction Bundle Upload - Datacenter Server

### Test Data

[Synthea][1] Test Data was generated with a Docker image created from the following Dockerfile:

```dockerfile
FROM gcr.io/distroless/java-debian10:11

WORKDIR /gen

ADD https://github.com/synthetichealth/synthea/releases/download/v2.7.0/synthea-with-dependencies.jar synthea.jar

CMD ["synthea.jar", "-s", "3256262546", "-cs", "3726451", "-r", "20210101", "-p", "100000", "--exporter.years_of_history=0", "--exporter.hospital.fhir.export=false", "--exporter.practitioner.fhir.export=true", "--exporter.use_uuid_filenames=true", "--generate.only_alive_patients=true"]
```

resulting in 35 GiB of gzip'ed JSON files.

### Test System

Dell PowerEdge R630 with 32 Cores of Intel(R) Xeon(R) CPU E5-2687W v4 @ 3.00GHz, 128 GB RAM, 500 GB on Dell SC4020 Storage with 1.92TB SAS SSD's.

### Start Script

```sh
docker run --name blaze --rm -v blaze-data:/app/data \
  -e JAVA_TOOL_OPTIONS="-Xmx16g" \
  -e LOG_LEVEL=debug \
  -p 8080:8080 \
  -p 8081:8081 \
  -e DB_MAX_BACKGROUND_JOBS=16 \
  -e DB_BLOCK_CACHE_SIZE=16384 \
  -e DB_RESOURCE_INDEXER_THREADS=30 \
  -e DB_RESOURCE_INDEXER_BATCH_SIZE=16 \
  -e DB_RESOURCE_CACHE_SIZE=500000 \
  -e DB_RESOURCE_HANDLE_CACHE_SIZE=1000000 \
  -d samply/blaze:0.11.0-alpha.6
```

### Relevant Startup Log Output

```text
Init RocksDB block cache of 16384 MB
Init RocksDB statistics
Init RocksDB statistics
Init RocksDB statistics
Init resource indexer executor with 30 threads
Open RocksDB key-value store in directory `/app/data/resource` with options: {:max-background-jobs 16, :compaction-readahead-size 0}
Open RocksDB key-value store in directory `/app/data/transaction` with options: {:max-background-jobs 16, :compaction-readahead-size 0}
Create resource cache with a size of 500000 resources
Create resource handle cache with a size of 1000000 resource handles
Open RocksDB key-value store in directory `/app/data/index` with options: {:max-background-jobs 16, :compaction-readahead-size 0}
Create transaction cache with a size of 100000 transactions
Open local database node with a resource indexer batch size of 16
Init FHIR transaction interaction executor with 32 threads
Init server executor with 32 threads
Init JSON parse executor with 32 threads
JVM version: 11.0.9.1
Maximum available memory: 16384 MiB
Number of available processors: 32
Successfully started Blaze version 0.11.0-alpha.6 in 22.2 seconds
```

### Upload Method

Command line tool `blazectl` v0.6.0 with concurrency of 8.

```text
Uploads          [total, concurrency]     100001, 8
Success          [ratio]                  100.00 %
Duration         [total]                  13h5m16s
Requ. Latencies  [mean, 50, 95, 99, max]  3.766s, 3.386s, 6.942s, 11.513s 2m10.432s
Proc. Latencies  [mean, 50, 95, 99, max]  3.766s, 3.386s, 6.942s, 11.513s 2m10.432s
Bytes In         [total, mean]            32.69 GiB, 342.72 KiB
Bytes Out        [total, mean]            408.01 GiB, 4.18 MiB
Status Codes     [code:count]             200:100001
```

The upload resulted in the following resource counts:

| Metric | Count |
| :--- | ---: |
| AllergyIntolerance | 66,388 |
| CarePlan | 980,220 |
| CareTeam | 980,220 |
| Claim | 16,714,056 |
| Condition | 2,677,145 |
| Device | 4,807 |
| DiagnosticReport | 15,460,152 |
| DocumentReference | 11,652,543 |
| Encounter | 11,652,543 |
| ExplanationOfBenefit | 11,652,543 |
| ImagingStudy | 75,558 |
| Immunization | 5,196,115 |
| Medication | 58,793 |
| MedicationAdministration | 58,793 |
| MedicationRequest | 5,061,513 |
| Observation | 75,581,015 |
| Patient | 100,000 |
| Practitioner | 9,174 |
| PractitionerRole | 9,174 |
| Procedure | 12,415,839 |
| Provenance | 100,000 |
| SupplyDelivery | 1,029,962 |
| **total** | **171,536,553** |

That are 171,536,553 resources in 13 hours and 5 minutes or about 3,600 resources per second.

The size of the database directory after the import was 234 GiB or about 1,43 kB per resource which is less than the uncompressed JSON transaction bundles.

## Transaction Bundle Upload - Developer Laptop

### Test Data

Generated 10,000 patients with Synthea master branch with Git SHA `4fed9eaf` and standard configuration using `./run_synthea -p 10000` resulting in 9.9 GiB of JSON files.

### Test System

MacBook Pro \(Retina, 15-inch, Mid 2015\) 2,5 GHz Intel Core i7, 16 GB RAM. Blaze version 0.9.0-alpha.26.

### Start Script

```sh
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
| :--- | ---: |
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

[1]: <https://github.com/synthetichealth/synthea>
