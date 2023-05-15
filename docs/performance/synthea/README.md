## Synthea Test Data Generator

## Build

```sh
docker build . -t synthea
```

## Run

```sh
docker run -v "$(pwd)/output:/gen/output" synthea 100
```

## Post-Process Bundles

```sh
./post-process-bundles.sh output/fhir
```

## Upload Bundles 

```sh
blazectl upload --server http://localhost:8080/fhir output/fhir
```
