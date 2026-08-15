# XML Paging Benchmark

This directory contains local profiling helpers for comparing Blaze JSON and
XML paging performance with a persistent Docker volume.

## Start Blaze

With a standalone jar:

```sh
make uberjar
profiling/xml-paging/run-standalone.sh target/blaze-1.7.0-standalone.jar
```

The script stores data below `profiling/xml-paging/runtime/data` by default.
Reusing that directory keeps the loaded resources across jar changes.

With Docker:

```sh
profiling/xml-paging/run-blaze.sh blaze:latest
```

The script uses the Docker volume `blaze-paging-data` by default. Reusing the
volume keeps the loaded resources across image changes.

## Upload Test Data

```sh
profiling/xml-paging/upload-test-data.sh /Users/axs/Projekte/kerndatensatz-testdaten/Test_Data
```

## Benchmark Paging

```sh
profiling/xml-paging/benchmark-paging.sh Encounter '_count=1000' json 5
profiling/xml-paging/benchmark-paging.sh Encounter '_count=1000' xml 5
```

Results are appended to `profiling/xml-paging/results.csv`.

For server-side response timing of one page without following next links:

```sh
profiling/xml-paging/benchmark-single-page.sh Encounter '_count=1000' json 20
profiling/xml-paging/benchmark-single-page.sh Encounter '_count=1000' xml 20
```
