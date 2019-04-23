# LIFE FHIR Store

[![Build Status](https://travis-ci.org/life-research/life-fhir-store.svg?branch=master)](https://travis-ci.org/life-research/life-fhir-store)
[![Dependencies Status](https://versions.deps.co/life-research/life-fhir-store/status.svg)](https://versions.deps.co/life-research/life-fhir-store)

A FHIR Store with internal, fast CQL Evaluation Engine

## Goal

The goal of this project is to provide a FHIR Store with an internal CQL Evaluation Engine which is able to answer population wide aggregate queries in a timely manner to enable interactive, online queries.

## State

The project is currently under active development. Essentially all official [CQL Tests][3] pass. Please report any issues you encounter during evaluation.

## Usage

You need Docker to run the LIFE FHIR Store. The most convenient way is to check out this repository and use the `docker-compose.yml` to bring the LIFE FHIR Store up. The default memory requirements are 4 GB. You also need to have port 8080 free on your host.

To start the LIFE FHIR Store, type:

```bash
docker-compose up
```

You should see a output similar to:

```
Creating volume "life-fhir-store_db-data" with default driver
Recreating life-fhir-store_db_1 ... done
Recreating life-fhir-store_store_1 ... done
Attaching to life-fhir-store_db_1, life-fhir-store_store_1
db_1     | Launching with Java options -server -Xms1g -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=50
db_1     | Starting datomic:free://0.0.0.0:4334/<DB-NAME>, storing data in: /data ...
db_1     | System started datomic:free://0.0.0.0:4334/<DB-NAME>, storing data in: /data
store_1  | 19-03-29 08:57:48 52816fd744d6 INFO [life-fhir-store.system:110] - Read structure definitions from: /app/fhir/r4 resulting in: Address, Annotation, Period, CodeableConcept, Organization, Encounter, Specimen, Bundle, Coding, Patient, Range, ContactPoint, Meta, Quantity, HumanName, DeviceMetric, SampledData, Ratio, Device, Reference, Identifier, Narrative, Observation
store_1  | 19-03-29 09:13:09 bc25669b9f17 INFO [life-fhir-store.system:90] - Created database at: datomic:free://db:4334/dev
store_1  | 19-03-29 09:13:09 bc25669b9f17 INFO [life-fhir-store.system:93] - Connect with database: datomic:free://db:4334/dev
store_1  | 19-03-29 09:13:10 bc25669b9f17 INFO [life-fhir-store.system:98] - Upsert schema in database: datomic:free://db:4334/dev creating 2300 new facts
store_1  | 19-03-29 09:13:10 bc25669b9f17 INFO [life-fhir-store.server:33] - Start server on port 8080
store_1  | 19-03-29 09:13:10 bc25669b9f17 INFO [life-fhir-store.core:49] - Maximum available memory: 2048
store_1  | 19-03-29 09:13:10 bc25669b9f17 INFO [life-fhir-store.core:50] - Number of available processors: 4
```

In order to test connectivity, you can query the health endpoint:

```bash
curl http://localhost:8080/health
```

It should return `OK`.

### Upload FHIR Resources

Before you can issue CQL queries against the LIFE FHIR Store, you have to upload some FHIR resources. If you have none, you can generate them y using the [FHIR Test Data Generator][1].

```bash
life-fhir-gen -n1 > bundle.json
```

Next you need to upload that `bundle.json` to the LIFE FHIR Store:

```bash
curl -d @bundle.json http://localhost:8080/fhir
```

The result should be:

```
{"message":"OK","t":<some number>}
```

If you like to upload your own resources, it's important, that the LIFE FHIR Store is currently configured to use a subset of FHIR R4. The available Resources can be seen at startup in the `Read structure definitions` output.

### Issuing a CQL Query

The most convenient way is to use the [CQL Runner][2]. You have to go into the `Config` menu and set the `CQL Engine` to `http://localhost:8080/cql/evaluate`. The other config options doesn't matter because the CQL Engine of the LIFE FHIR Store always uses its own internal data.

### Deleting the Data Volume

If you like to restart with a fresh database, you have to delete the data volume. You can do this by typing:

```bash
docker volume rm life-fhir-store_db-data
```

## License

Copyright © 2019 LIFE Research Center (Alexander Kiel)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: <https://github.com/life-research/life-fhir-gen>
[2]: <http://cql-runner.dataphoria.org/>
[3]: <https://cql.hl7.org/tests.html>
