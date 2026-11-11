# Profiling

The performance test scripts of the [CQL](cql.md) and [FHIR Search](fhir-search.md) sections can record a profile of Blaze with [async-profiler][1] while they run. This page describes how to set up Blaze for profiling and how to run the scripts.

## Test Scripts

The test scripts under `docs/performance/fhir-search` and the `search-all*.sh` scripts under `docs/performance/cql` run a series of named tests against Blaze. Each script takes the Docker Compose file of the Blaze under test as first argument. The FHIR Search scripts take the base URL of Blaze as optional second argument, which defaults to `http://localhost:8080/fhir`:

```sh
docs/performance/fhir-search/simple-code-search.sh docker-compose.yml
```

Between tests, the scripts restart Blaze with `docker compose up --force-recreate --wait`, so that every test starts with empty in-memory caches of Blaze. Each test runs once for warmup and then the number of times given in the `REPEATS` environment variable, which defaults to 3. Only the runs after the warmup are taken for the statistics.

## Docker Setup

The scripts run the async-profiler command line tools inside the container of the Compose service `blaze`. Because the Blaze image doesn't contain async-profiler, it has to be installed on the host and mounted into the container under `/opt/async-profiler`. Use the Linux release of async-profiler matching the CPU architecture of the container, like `linux-x64` or `linux-arm64`.

In addition, Blaze has to load async-profiler as agent at JVM start. To do so, add `-agentpath:/opt/async-profiler/lib/libasyncProfiler.so` to the `JAVA_TOOL_OPTIONS` environment variable. The scripts then control the already loaded agent instead of attaching to the running JVM.

The service also needs a healthcheck, so that `docker compose up --wait` waits until Blaze is ready.

```yaml
services:
  blaze:
    image: "samply/blaze:latest"
    environment:
      JAVA_TOOL_OPTIONS: "-Xmx16g -agentpath:/opt/async-profiler/lib/libasyncProfiler.so"
    ports:
    - "8080:8080"
    volumes:
    - "blaze-data:/app/data"
    - "/opt/async-profiler:/opt/async-profiler:ro"
    healthcheck:
      test: [ "CMD", "wget", "--spider", "http://localhost:8080/health" ]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
      start_interval: 1s
volumes:
  blaze-data:
```

If async-profiler is mounted to another directory inside the container, change the agent path accordingly and set the environment variable `ASYNC_PROFILER_HOME` to that directory when running the scripts.

## Running a Profile

The test to profile is selected by the `PROFILE` environment variable, which holds a glob pattern matching test names. The names of the tests are the second arguments of the `profile` calls in the scripts, like `count-72514-3` in `fhir-search/simple-code-search.sh` or `observation-17861-6` in `cql/search-all.sh`.

```sh
PROFILE=count-72514-3 docs/performance/fhir-search/simple-code-search.sh docker-compose.yml
```

With `PROFILE` set, the scripts run only the tests matching the pattern and skip all others. A test runs on a freshly restarted Blaze. The profiler starts after the warmup run and stops after the last run, so that the profile covers only the measured runs.

The scripts write three files into the current directory, named after the test:

| File            | Content                                                                                                     |
|-----------------|-------------------------------------------------------------------------------------------------------------|
| `<name>.jfr`    | the recording in the JDK Flight Recorder format, with every sample including its thread and its timestamp   |
| `<name>.folded` | the folded stacks with the thread as first frame, suitable for diffing and further processing                |
| `<name>.html`   | an interactive flame graph                                                                                  |

### Environment Variables

| Variable              | Description                                                                                                                             | Default               |
|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------------|-----------------------|
| `PROFILE`             | glob pattern of the names of the tests to profile; nothing is profiled if unset                                                         |                       |
| `PROFILE_EVENT`       | the async-profiler event, like `wall`, `cpu`, `alloc` or `lock`                                                                         | `wall`                |
| `REPEATS`             | the number of runs after the warmup, which are the profiled ones; increase it to get more samples of fast tests                         | `3`                   |
| `TEST`                | glob pattern of the names of the tests to run; overrides the selection by `PROFILE`, like to run more tests than are profiled            | `PROFILE`             |
| `ASYNC_PROFILER_HOME` | the directory of async-profiler inside the `blaze` container                                                                             | `/opt/async-profiler` |

For example, the following records allocations over 32 measured runs:

```sh
PROFILE=count-72514-3 PROFILE_EVENT=alloc REPEATS=32 docs/performance/fhir-search/simple-code-search.sh docker-compose.yml
```

`TEST` without `PROFILE` runs the selected tests without profiling, which is useful to repeat a single measurement.

[1]: <https://github.com/async-profiler/async-profiler>
