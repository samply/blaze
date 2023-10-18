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

While Synthea is running, it's important to run alrady the post-process loop. Otherwise the amount of data Synthea generates will likely fill your disk with uncompressed FHIR bundles. 

```sh
./post-process-bundle-loop.sh output/fhir
```

## Upload Bundles 

```sh
blazectl upload --server http://localhost:8080/fhir output/fhir
```
