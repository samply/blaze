# Blaze

A libre [HL7 FHIR](https://hl7.org/fhir/) -compliant Data Store with a high-performance CQL Evaluation Engine.

[![Latest Release](https://img.shields.io/github/v/release/samply/blaze)][5]
[![Docker Pulls](https://img.shields.io/docker/pulls/samply/blaze.svg)](https://hub.docker.com/r/samply/blaze/)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/samply/blaze/badge)](https://scorecard.dev/viewer/?uri=github.com/samply/blaze)
[![Code Coverage](https://codecov.io/gh/samply/blaze/branch/develop/graph/badge.svg)](https://codecov.io/gh/samply/blaze)
[![Build Status](https://github.com/samply/blaze/actions/workflows/build.yml/badge.svg)](https://github.com/samply/blaze/actions/workflows/build.yml)


## About

Blaze is a component of the German [Medical Informatics Initiative](https://www.medizininformatik-initiative.de) and the [European Biobanking Medical Research Infrastructure](https://www.bbmri-eric.eu). As such, Blaze is currently used by 40+ Medical Informatics institutions in Germany and across Europe. A 1.0 version, focused on full FHIR4 compliance, is currently in the works.


## Distinctive Features

Blaze was designed to satisfy interactive (or, at least, low-latency) scenarios of population-wide aggregate queries in distributed environments.
Currently, it is the only existing HL7 FHIR -compliant Data Store that satisfies this scenario.


## Latest Release

Latest release: [v0.30.0][5]


## Try it online!

[Demo Blaze installation at the University of Leipzig, Germany](https://blaze.life.uni-leipzig.de/fhir). _(user/password: demo)_


## Run it on your own IT infrastructure!

There are different ways to use Blaze.

### via Docker

Probably the simplest way to use Blaze locally is via a Docker image.

```sh
docker volume create blaze-data
docker run -p 8080:8080 -v blaze-data:/app/data samply/blaze:latest
```

With this setup, Blaze will store all of its instance-specific data inside the `blaze-data` volume, which is to be used on subsequent starts.

### via other options

For other ways to run and deploy Blaze, please refer to [Deployment](docs/deployment/README.md).


## Contributing

We welcome Pull Requests!
All we ask is to please first read the [development](DEVELOPMENT.md) documentation.


## Documentation

* [Deployment](docs/deployment/README.md)
* [FHIR RESTful API](docs/api.md)
* [Frontend (Web UI)](docs/frontend.md)
* [Importing Data](docs/importing-data.md)
* [Sync Data](docs/data-sync.md)
* [Conformance](docs/conformance.md)
* [Performance](docs/performance.md)
* [Monitoring](docs/monitoring.md)
* [Tuning Guide](docs/tuning-guide.md)
* [Tooling](docs/tooling.md)
* [CQL Queries](docs/cql-queries.md)
* [Authentication](docs/authentication.md)
* [Architecture](docs/architecture.md)
* [Implementation](docs/implementation/README.md)


## YourKit Profiler

![YourKit logo](https://www.yourkit.com/images/yklogo.png)

The developers of Blaze use the YourKit profiler to optimize performance. YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler][6], [YourKit .NET Profiler][7] and [YourKit YouMonitor][8].


## License

Copyright 2019-2024, The Blaze Community.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.


## References

[3]: <https://cql.hl7.org/tests.html>
[4]: <https://alexanderkiel.gitbook.io/blaze/deployment>
[5]: <https://github.com/samply/blaze/releases/tag/v0.30.0>
[6]: <https://www.yourkit.com/java/profiler/>
[7]: <https://www.yourkit.com/.net/profiler/>
[8]: <https://www.yourkit.com/youmonitor/>
[9]: <https://github.com/facebook/rocksdb/wiki/Setup-Options-and-Basic-Tuning#block-cache-size>
[10]: <https://github.com/facebook/rocksdb/wiki/RocksDB-Basics#multi-threaded-compactions>
[12]: <https://touchstone.aegis.net/touchstone/conformance/history?suite=FHIR4-0-1-Basic-Server&supportedOnly=true&suiteType=HL7_FHIR_SERVER&ownedBy=ALL&ps=10&published=true&pPass=0&strSVersion=6&format=ALL>
