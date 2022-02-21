# Evaluate a CQL Query using the Command Line

This section describes how to evaluate a CQL query using the command line only.

## Run Blaze

If you don't already have Blaze running, you can read about how to do it in [Deployment](../deployment/README.md). If you have Docker available just run:

```
docker run -p 8080:8080 -v blaze-data:/app/data samply/blaze:0.16
```

## Use the evaluate-measure.sh Script

An easy way to evaluate queries is to use the script `evaluate-measure.sh`, which is available at the root of the repository.

```sh
$ ./evaluate-measure.sh
Usage: ./evaluate-measure.sh -f QUERY_FILE [ -t subject-type ] [ -r report-type ] BASE

Example subject-types: Patient, Specimen; default is Patient
Possible report-types: subject-list, population; default is population 
```

### Examples

#### Counting the Number of Male Patients

```sh
$ ./evaluate-measure.sh -f docs/cql-queries/gender-male.cql http://localhost:8080/fhir 
```
the result should be something like this:

```text
Generating a population count report...
Found 43 subjects.
```

#### Listing the URLs of Male Patients

```sh
$ ./evaluate-measure.sh -f docs/cql-queries/gender-male.cql -r subject-list http://localhost:8080/fhir 
```
the result should be something like this:

```text
Generating a report including the list of matching subjects...
Found 43 subjects that can be found on List http://localhost:8080/fhir/List/C7UW434TIAF7CDYC.
The individual subject URLs are:
http://localhost:8080/fhir/Patient/bbmri-0
http://localhost:8080/fhir/Patient/bbmri-1
http://localhost:8080/fhir/Patient/bbmri-13
...
```
