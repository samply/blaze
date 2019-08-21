# Blaze

[![Build Status](https://travis-ci.org/life-research/blaze.svg?branch=master)](https://travis-ci.org/life-research/blaze)
[![Dependencies Status](https://versions.deps.co/life-research/blaze/status.svg)](https://versions.deps.co/life-research/blaze)
[![codecov](https://codecov.io/gh/life-research/blaze/branch/master/graph/badge.svg)](https://codecov.io/gh/life-research/blaze)
[![Docker Pulls](https://img.shields.io/docker/pulls/liferesearch/blaze.svg)](https://hub.docker.com/r/liferesearch/blaze/)
[![Image Layers](https://images.microbadger.com/badges/image/liferesearch/blaze.svg)](https://microbadger.com/images/liferesearch/blaze)

A FHIR® Store with internal, fast CQL Evaluation Engine

## Goal

The goal of this project is to provide a FHIR® Store with an internal CQL Evaluation Engine which is able to answer population wide aggregate queries in a timely manner to enable interactive, online queries.

## State

The project is currently under active development. Essentially all official [CQL Tests][3] pass. Please report any issues you encounter during evaluation.

## Installation

The installation of Blaze is described in the [Installation Section][4] of the Blaze documentation.

## Usage

In order to test connectivity, you can query the health endpoint:

```bash
curl http://localhost:8080/health
```

It should return `OK`.

### Upload FHIR Resources

Before you can issue CQL queries against Blaze, you have to upload some FHIR resources. If you have none, you can generate them by using the [FHIR Test Data Generator][1].

```bash
life-fhir-gen -n1 > bundle.json
```

Next you need to upload that `bundle.json` to Blaze:

```bash
curl -d @bundle.json http://localhost:8080/fhir
```

The result should be:

```
{"message":"OK","t":<some number>}
```

If you like to upload your own resources, it's important, that Blaze is currently configured to use a subset of FHIR R4. The available Resources can be seen at startup in the `Read structure definitions` output.

### Issuing a CQL Query

The most convenient way is to use the [CQL Runner][2]. You have to go into the `Config` menu and set the `CQL Engine` to `http://localhost:8080/cql/evaluate`. The other config options doesn't matter because the CQL Engine of Blaze always uses its own internal data.

As a test query you can use
```
using FHIR version '4.0.0'
context Patient
context Unspecified

define NumberOfPatients:
  Count([Patient])

define AllPatients:
  [Patient]
```
The result should be something like

```
>> NumberOfPatients [7:1] 10
>> Patient [10:1] [ {
  "birthDate" : "AgfNBQg=",
  "id" : "1001",
  "gender" : "male",
  "resourceType" : "Patient"
}, {
  "birthDate" : "AgfOARE=",
  "id" : "1002",
  "gender" : "female",
  "resourceType" : "Patient"
} ]
```

### Deleting the Data Volume

If you like to restart with a fresh database, you have to delete the data volume. You can do this by typing:

```bash
docker volume rm blaze_db-data
```

## License

Copyright © 2019 LIFE Research Center (Alexander Kiel)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: <https://github.com/life-research/life-fhir-gen>
[2]: <http://cql-runner.dataphoria.org/>
[3]: <https://cql.hl7.org/tests.html>
[4]: <https://life-research.github.io/blaze/#_installation>
