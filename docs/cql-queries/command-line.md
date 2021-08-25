# Evaluate a CQL Query using the Command Line

This section describes how to evaluate a CQL query using the command line only.

## Run Blaze

If you don't already have Blaze running, you can read about how to do it in [Deployment](../deployment/README.md). If you have Docker available just run:

```
docker run -p 8080:8080 -v blaze-data:/app/data samply/blaze:0.11
```

## Use the evaluate-measure.sh Script

An easy way to evaluate queries is to use the script `evaluate-measure.sh`, which is available at the root of the repository.

```sh
$ ./evaluate-measure.sh
Usage: ./evaluate-measure.sh -f QUERY_FILE [ -t type ] BASE 
```

### Example 

```sh
$ ./evaluate-measure.sh -f docs/cql-queries/gender-male.cql http://localhost:8080/fhir 
```
the result should be

```text
Count: 0
```

if no data was imported.
