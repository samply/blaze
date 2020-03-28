# Blaze

[![Build Status](https://travis-ci.org/samply/blaze.svg?branch=master)](https://travis-ci.org/samply/blaze)
[![Docker Pulls](https://img.shields.io/docker/pulls/samply/blaze.svg)](https://hub.docker.com/r/samply/blaze/)
[![Image Layers](https://images.microbadger.com/badges/image/samply/blaze.svg)](https://microbadger.com/images/samply/blaze)

A FHIR® Store with internal, fast CQL Evaluation Engine

## Goal

The goal of this project is to provide a FHIR® Store with an internal CQL Evaluation Engine which is able to answer population wide aggregate queries in a timely manner to enable interactive, online queries.

## State

The project is currently under active development. Essentially all official [CQL Tests][3] pass. Please report any issues you encounter during evaluation.

Latest release: [v0.8.0-alpha.9][5]

## Quick Start

In order to run Blaze with an in-memory, volatile database, just execute the following:

### Docker

```bash
docker run -p 8080:8080 samply/blaze:0.8.0-alpha.9
```

### Java

```bash
wget https://github.com/samply/blaze/releases/download/v0.8.0-alpha.9/blaze-0.8.0-alpha.9-standalone.jar
java -jar blaze-0.8.0-alpha.9-standalone.jar
```

Logging output should appear which prints the most important settings and system parameters like Java version and available memory.

In order to test connectivity, query the health endpoint:

```bash
curl http://localhost:8080/health
```

## Deployment

In-deep deployment options of Blaze are described in the [Deployment Section][4] of the Blaze documentation.

## YourKit Profiler

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

The developers of Blaze uses the YourKit profiler to optimize performance. YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler][6], [YourKit .NET Profiler][7] and [YourKit YouMonitor][8].

## License

Copyright 2019 The Samply Development Community

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[3]: <https://cql.hl7.org/tests.html>
[4]: <https://alexanderkiel.gitbook.io/blaze/deployment>
[5]: <https://github.com/samply/blaze/releases/tag/v0.8.0-alpha.9>
[6]: <https://www.yourkit.com/java/profiler/>
[7]: <https://www.yourkit.com/.net/profiler/>
[8]: <https://www.yourkit.com/youmonitor/>
