# Blaze

[![Build Status](https://github.com/samply/blaze/workflows/Build/badge.svg)](https://github.com/samply/blaze/actions?query=workflow%3ABuild)
[![Docker Pulls](https://img.shields.io/docker/pulls/samply/blaze.svg)](https://hub.docker.com/r/samply/blaze/)
[![codecov](https://codecov.io/gh/samply/blaze/branch/develop/graph/badge.svg)](https://codecov.io/gh/samply/blaze)

A FHIR® Store with internal, fast CQL Evaluation Engine

## Goal

The goal of this project is to provide a FHIR® Store with an internal CQL Evaluation Engine which is able to answer population wide aggregate queries in a timely manner to enable interactive, online queries.

## State

Blaze passes all [Touchstone FHIR 4.0.1 Basic Tests][12] and almost all [CQL Tests][3]. Please refer to the [Conformance](docs/conformance.md) section and report any issues you encounter during evaluation.

Latest release: [v0.11.0][5]

## Quick Start

In order to run Blaze just execute the following:

### Docker

```sh
docker volume create blaze-data
docker run -p 8080:8080 -v blaze-data:/app/data samply/blaze:0.11.0
```

Blaze will create multiple directories inside the `blaze-data` volume on its first start and use the same directories on subsequent starts.

Please refer to [Docker Deployment](docs/deployment/docker-deployment.md) for the full documentation.

### Standalone Java without Docker

Please have a look into [Manual Deployment](docs/deployment/manual-deployment.md).

## Documentation

* [Deployment](docs/deployment/README.md)
* [FHIR RESTful API](docs/api.md)
* [Importing Data](docs/importing-data.md)
* [Sync Data](docs/data-sync.md)
* [Conformance](docs/conformance.md)
* [Performance](docs/performance.md)
* [Tuning Guide](docs/tuning-guide.md)
* [Tooling](docs/tooling.md)
* [CQL Queries](docs/cql-queries.md)
* [Authentication](docs/authentication.md)
* [Architecture](docs/architecture.md)
* [Implementation](docs/implementation/README.md)

## YourKit Profiler

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

The developers of Blaze uses the YourKit profiler to optimize performance. YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler][6], [YourKit .NET Profiler][7] and [YourKit YouMonitor][8].

## License

Copyright 2019 - 2021 The Samply Community

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[3]: <https://cql.hl7.org/tests.html>
[4]: <https://alexanderkiel.gitbook.io/blaze/deployment>
[5]: <https://github.com/samply/blaze/releases/tag/v0.11.0>
[6]: <https://www.yourkit.com/java/profiler/>
[7]: <https://www.yourkit.com/.net/profiler/>
[8]: <https://www.yourkit.com/youmonitor/>
[9]: <https://github.com/facebook/rocksdb/wiki/Setup-Options-and-Basic-Tuning#block-cache-size>
[10]: <https://github.com/facebook/rocksdb/wiki/RocksDB-Basics#multi-threaded-compactions>
[12]: <https://touchstone.aegis.net/touchstone/conformance/history?suite=FHIR4-0-1-Basic-Server&supportedOnly=true&suiteType=HL7_FHIR_SERVER&ownedBy=ALL&ps=10&published=true&pPass=0&strSVersion=1&format=ALL>
