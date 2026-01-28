<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const digest = import.meta.env.VITE_LATEST_DIGEST;
  const tag = release.substring(1);
</script>

# Evaluate a CQL Measure using the Command Line

This section describes how to evaluate a CQL measure using the command line only.

## Checkout the Project

This section assumes, that you have checked out the project and open a command line in its directory.

```sh
git clone https://github.com/samply/blaze.git
cd blaze
```

## Run Blaze

If you don't already have Blaze running, you can read about how to do it in [Deployment](../deployment.md). If you have Docker available just run:

```sh-vue
docker run -p 8080:8080 -v blaze-data:/app/data samply/blaze:{{ tag }}@{{ digest }}
```

## Import some data

If you just started Blaze without any data, you can import some using the [blazectl](https://github.com/samply/blazectl) command:

```sh
blazectl upload --server http://localhost:8080/fhir .github/test-data/synthea
```

## Use the evaluate-measure.sh Script

An easy way to evaluate queries is to use the script `evaluate-measure.sh`, which is available at the root of the repository.

```sh
./evaluate-measure.sh
Usage: ./evaluate-measure.sh -f QUERY_FILE [ -t subject-type ] [ -r report-type ] BASE

Example subject-types: Patient, Specimen; default is Patient
Possible report-types: subject-list, population; default is population 
```

### Examples

#### Counting the Number of Male Patients

```sh
./evaluate-measure.sh -f docs/cql-queries/gender-male.cql http://localhost:8080/fhir
```
the result should be something like this:

```text
Generating a population count report...
Found 57 patients.
```

#### Listing all Male Patients

```sh
./evaluate-measure.sh -f docs/cql-queries/gender-male.cql -r subject-list http://localhost:8080/fhir
```
the result should be something like this:

```text
Generating a report including the list of matching patients...
Found 57 patients that can be found on List http://localhost:8080/fhir/List/C745YHPCXXA6W7YK.

Please use the following blazectl command to download the patients:
  blazectl download --server http://localhost:8080/fhir Patient -q '_list=C745YHPCXXA6W7YK' -o patients.ndjson
```

If you can execute:

```sh
blazectl download --server http://localhost:8080/fhir Patient -q '_list=C745YHPCXXA6W7YK' -o patients.ndjson
```

You will find all Patient resources in the file `patients.ndjson` one resource per line.
