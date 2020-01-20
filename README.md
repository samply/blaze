# Blaze

[![Build Status](https://travis-ci.org/life-research/blaze.svg?branch=master)](https://travis-ci.org/life-research/blaze)
[![Docker Pulls](https://img.shields.io/docker/pulls/liferesearch/blaze.svg)](https://hub.docker.com/r/liferesearch/blaze/)
[![Image Layers](https://images.microbadger.com/badges/image/liferesearch/blaze.svg)](https://microbadger.com/images/liferesearch/blaze)

A FHIR® Store with internal, fast CQL Evaluation Engine

## Goal

The goal of this project is to provide a FHIR® Store with an internal CQL Evaluation Engine which is able to answer population wide aggregate queries in a timely manner to enable interactive, online queries.

## State

The project is currently under active development. Essentially all official [CQL Tests][3] pass. Please report any issues you encounter during evaluation.

Latest release: [v0.8.0-alpha.7][5]

## Quick Start

In order to run Blaze with an in-memory, volatile database, just execute the following:

### Docker

```bash
docker run -p 8080:8080 liferesearch/blaze:0.8.0-alpha.7
```

### Java

```bash
wget https://github.com/life-research/blaze/releases/download/v0.8.0-alpha.7/blaze-0.8.0-alpha.7-standalone.jar
java -jar blaze-0.8.0-alpha.7-standalone.jar
```

Logging output should appear which prints the most important settings and system parameters like Java version and available memory.

In order to test connectivity, query the health endpoint:

```bash
curl http://localhost:8080/health
```

## Deployment

In-deep deployment options of Blaze are described in the [Deployment Section][4] of the Blaze documentation.

## License

Copyright 2019 The Samply Development Community

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[1]: <https://github.com/life-research/life-fhir-gen>
[2]: <http://cql-runner.dataphoria.org/>
[3]: <https://cql.hl7.org/tests.html>
[4]: <https://alexanderkiel.gitbook.io/blaze/deployment>
[5]: <https://github.com/samply/blaze/releases/tag/v0.8.0-alpha.7>
