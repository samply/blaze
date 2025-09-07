<img src="docs/public/blaze-logo.svg" alt="Blaze" height="48">

[![Build](https://github.com/samply/blaze/actions/workflows/build.yml/badge.svg)](https://github.com/samply/blaze/actions/workflows/build.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/samply/blaze/badge)](https://scorecard.dev/viewer/?uri=github.com/samply/blaze)
[![Docker Pulls](https://img.shields.io/docker/pulls/samply/blaze.svg)](https://hub.docker.com/r/samply/blaze/)
[![Code Coverage](https://codecov.io/gh/samply/blaze/branch/develop/graph/badge.svg)](https://codecov.io/gh/samply/blaze)
[![Latest Release](https://img.shields.io/github/v/release/samply/blaze?color=1874a7)][5]
[![Docs](https://img.shields.io/badge/Docs-blue.svg)](https://samply.github.io/blaze)

A FHIR® Server with internal, fast CQL Evaluation Engine

## News

Alexander Kiel will give a talk about Blaze at the [HL7® FHIR® DevDays 2025](https://www.devdays.com/program-2025) at Wed, June 4 11:15 - 11:35. 

## Goal

The goal of this project is to provide a FHIR® Server with an internal CQL Evaluation Engine which is able to answer population wide aggregate queries in a timely manner to enable interactive, online queries over millions of patients.

## Demo

A demo installation can be found [here](https://blaze.life.uni-leipzig.de/fhir) (user/password: demo).

## State

Blaze is stable and widely used in the [Medical Informatics Initiative](https://www.medizininformatik-initiative.de) in Germany and in [Biobanks](https://www.bbmri-eric.eu) across Europe.

Latest release: [v1.1.1][5]

## Key Features

* Implements large parts of the [FHIR® R4 API][1]
* Contains a fast [CQL Evaluation Engine][17]
* Supports the operations [$evaluate-measure][2], [$everything][13], [$validate-code][14], [$expand][15] amongst others
* Offers [terminology services][16] including LOINC and SNOMED CT
* Scales horizontally via [Distributed Storage Variant][18]
* Comes with a modern [Web Frontend][19]

## Documentation

Documentation can be found [here](https://samply.github.io/blaze).

## Quick Start

Blaze can be started with a single command using docker:

```sh
docker run -d --name blaze -p 8080:8080 samply/blaze:latest
```

## YourKit Profiler

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

The developers of Blaze uses the YourKit profiler to optimize performance. YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler][6], [YourKit .NET Profiler][7] and [YourKit YouMonitor][8].

## License

Copyright 2019 - 2025 The Samply Community

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[1]: <https://samply.github.io/blaze/api.html>
[2]: <https://samply.github.io/blaze/api/operation/measure-evaluate-measure.html>
[3]: <https://cql.hl7.org/tests.html>
[4]: <https://alexanderkiel.gitbook.io/blaze/deployment>
[5]: <https://github.com/samply/blaze/releases/tag/v1.1.1>
[6]: <https://www.yourkit.com/java/profiler/>
[7]: <https://www.yourkit.com/.net/profiler/>
[8]: <https://www.yourkit.com/youmonitor/>
[9]: <https://github.com/facebook/rocksdb/wiki/Setup-Options-and-Basic-Tuning#block-cache-size>
[10]: <https://github.com/facebook/rocksdb/wiki/RocksDB-Basics#multi-threaded-compactions>
[12]: <https://touchstone.aegis.net/touchstone/conformance/history?suite=FHIR4-0-1-Basic-Server&supportedOnly=true&suiteType=HL7_FHIR_SERVER&ownedBy=ALL&ps=10&published=true&pPass=0&strSVersion=6&format=ALL>
[13]: <https://samply.github.io/blaze/api/operation/patient-everything.html>
[14]: <https://samply.github.io/blaze/api/operation/code-system-validate-code.html>
[15]: <https://samply.github.io/blaze/api/operation/value-set-expand.html>
[16]: <https://samply.github.io/blaze/terminology-service.html>
[17]: <https://samply.github.io/blaze/cql-queries.html>
[18]: <https://samply.github.io/blaze/deployment/distributed-backend.html>
[19]: <https://samply.github.io/blaze/frontend.html>
