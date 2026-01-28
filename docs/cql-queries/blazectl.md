<script setup lang="ts">
  const release = import.meta.env.VITE_LATEST_RELEASE;
  const digest = import.meta.env.VITE_LATEST_DIGEST;
  const tag = release.substring(1);
</script>

# CQL Queries with blazectl

This section describes how to evaluate a CQL measure using the command line only.

## Checkout the Project

This section assumes, that you have checked out the project and open a command line in its directory.

```sh
git clone https://github.com/samply/blaze.git
cd blaze
```

## Install Blazectl

[Blazectl](https://github.com/samply/blazectl) is a command line utility that can, among other things, evaluate a CQL measure against a Blaze server available via HTTP. You'll find the installation instructions for your platform in its [README](https://github.com/samply/blazectl).

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

## Count all Male Patients

The goal is to write a measure that counts all male patients.

```sh
blazectl evaluate-measure docs/cql-queries/gender-male.yml --server http://localhost:8080/fhir
```

Blazectl will output a [MeasureReport](http://www.hl7.org/fhir/measurereport.html) resource. The important parts are:

```json
{
  "resourceType": "MeasureReport",
  "group": [
    {
      "population": [
        {
          "code": {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/measure-population",
                "code": "initial-population"
              }
            ]
          },
          "count": 56
        }
      ]
    }
  ]
}
```

You can use [jq](https://stedolan.github.io/jq) to extract the count from the MeasureReport:

```sh
blazectl evaluate-measure docs/cql-queries/gender-male.yml --server http://localhost:8080/fhir | jq '.group[0].population[0].count'
```

Now the output should be:

```text
Evaluate measure with canonical URL urn:uuid:73a6023d-5a91-4822-9e36-48ebc2a8ed09 on http://localhost:8080/fhir ...

56
```

### The Measure YAML

Because evaluating a measure requires one to create a [Measure](http://www.hl7.org/fhir/measure.html) and a [Library](http://www.hl7.org/fhir/library.html) resource on Blaze and execute the [$evaluate-measure](https://www.hl7.org/fhir/operation-measure-evaluate-measure.html) operation, Blazectl takes a simplified Measure resource in YAML format. It looks like this:

```yaml
library: docs/cql-queries/gender-male.cql
group:
- type: Patient
  population:
  - expression: InInitialPopulation
```

* you reference the CQL library file under the `library` key
* you can specify several groups were you can define populations based on resource type.
* under `population` you specify the name of a CQL expression which has to be present in the CQL library file. Here the expression is called `InInitialPopulation`.

### The CQL Library File

The CQL library file looks like this:

```text
library "gender-male"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  Patient.gender = 'male'
```

You can read more about CQL in the [Author's Guide](https://cql.hl7.org/02-authorsguide.html) and in the [CQL Reference](https://cql.hl7.org/09-b-cqlreference.html). The important bit here is the expression definition:

```text
define InInitialPopulation:
  Patient.gender = 'male'
```

In it, the expression `Patient.gender = 'male'` is given the name `InInitialPopulation` that can be used in the Measure YAML.

## Calculate the Distribution of all Patients Birth Years

The goal is to write a Measure that will output the distribution of all patients birth years including a conversation into the CSV format for easy consumption of analysis tools.

First try the command:

```sh
blazectl evaluate-measure docs/cql-queries/stratifier-birth-year.yml --server http://localhost:8080/fhir | jq -rf docs/cql-queries/stratifier-birth-year.jq
```

The (shortened) output should be:

```text
Evaluate measure with canonical URL urn:uuid:d1826406-431d-4993-ace0-6e5e96ee1b48 on http://localhost:8080/fhir ...

"year","count"
"1919",3
"1929",7
"1939",1
"1942",1
"1943",2
"1944",1
"1945",2
"1946",5
"1947",1
"1948",1
```

Now lets look at the three parts, the Measure YAML, the CQL library file and the jq file:

### The Measure YAML

```yaml
library: docs/cql-queries/stratifier-birth-year.cql
group:
- type: Patient
  population:
  - expression: InInitialPopulation
  stratifier:
  - code: birth-year
    expression: BirthYear
```

The first part of the Measure YAML looks exactly like the one above. The new part is the `stratifier`. Stratifiers group members of populations according to the specified expression. Here the population consists of patients and the expression is called `BirthYear`. So that stratifier will group patients by their birth years. 

### The CQL Library File

The CQL library file looks like this:

```text
library "stratifier-birth-year"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  true

define BirthYear:
  year from Patient.birthDate
```

Here the expression `InInitialPopulation` is defined as `true` in order to include all patients. The expression called `BirthYear` that is used as stratifier will take the birth date from the patient and return the year component of it.

### The jq File

The `jq` file doing the CSV conversation looks like this:

```text
["year", "count"],
(.group[0].stratifier[0].stratum[] | [.value.text, .population[0].count])
| @csv
```

Here `["year", "count"]` represents the CSV header and `(.group[0].stratifier[0].stratum[] | [.value.text, .population[0].count])` extracts the value and the count out of the strata of the MeasureReport. The syntax of `jq` is documented in its [manual](https://stedolan.github.io/jq/manual).

## Calculate the Distribution of all Condition Codes

The goal is to write a Measure that will output the distribution of all condition codes including a conversation into the CSV format for easy consumption of analysis tools.

First try the command:

```sh
blazectl evaluate-measure docs/cql-queries/stratifier-condition-code.yml --server http://localhost:8080/fhir | jq -rf docs/cql-queries/stratifier-condition-code.jq
```

The (shortened) output should be:

```text
Evaluate measure with canonical URL urn:uuid:4f8176a7-d37a-486f-9f66-5e3849e7013f on http://localhost:8080/fhir ...

"system","code","display","count"
"http://snomed.info/sct","241929008","Acute allergic reaction",3
"http://snomed.info/sct","75498004","Acute bacterial sinusitis (disorder)",8
"http://snomed.info/sct","10509002","Acute bronchitis (disorder)",63
"http://snomed.info/sct","132281000119108","Acute deep venous thrombosis (disorder)",2
"http://snomed.info/sct","706870000","Acute pulmonary embolism (disorder)",9
"http://snomed.info/sct","67782005","Acute respiratory distress syndrome (disorder)",3
"http://snomed.info/sct","65710008","Acute respiratory failure (disorder)",14
"http://snomed.info/sct","195662009","Acute viral pharyngitis (disorder)",69
"http://snomed.info/sct","7200002","Alcoholism",1
```

Now lets look at the three parts, the Measure YAML, the CQL library file and the jq file:

### The Measure YAML

```yaml
library: docs/cql-queries/stratifier-condition-code.cql
group:
- type: Condition
  population:
  - expression: InInitialPopulation
  stratifier:
  - code: code
    expression: Code
```

The key difference to the former Measure YAML files is the group type of `Condition`. That type defines all populations of this group to consist of Condition resources instead of Patient resources. This also means that the population expressions defined in this group have to return Condition resources instead of a boolean value. We will see that in a moment. The stratifier looks the same as in former files, but its expression `Code` will now operate on Condition resources instead of patients.

### The CQL Library File

The CQL library file looks like this:

```text
library "stratifier-condition-code"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

context Patient

define InInitialPopulation:
  [Condition]

define function Code(condition Condition):
  condition.code
```

Here we have our `InInitialPopulation` expression defined as `[Condition]`. The expression `[Condition]` retrieves all Condition resources of the current selected patient. Doing so will return the required type of Condition resources from the population expression, we discussed above. If you like to constrain the patients from which Condition resources are returned, you have to do it in the `InInitialPopulation` expression.

Next we define a function called `Code` what will extract the code from a Condition resource. Here the expression `condition.code` is a [FHIRPath](http://hl7.org/fhirpath/) expression as CQL is a superset of FHIRPath.

### The jq File

The `jq` file doing the CSV conversation looks like this:

```text
["system", "code", "display", "count"],
(.group[0].stratifier[0].stratum[]
  | [.value.coding[0].system, .value.coding[0].code, .value.coding[0].display, .population[0].count])
| @csv
```

Because we return a `CodeableConcept` from our stratifier expression `Code`, the stratum values will contain that `CodeableConcept`s directly. In the `jq` script, we extract the `system`, `code` and `display` parts of the first Coding using the expression `.value.coding[0].system` and others. If the Condition resources use more than one Coding, its easy to add columns with the later Codings. In the Synthea dataset that's not the case.

## Calculate the Distribution of all Body Weights

The goal is to write a Measure that will output the distribution of all body weights including a conversation into the CSV format for easy consumption of analysis tools.

First try the command:

```sh
blazectl evaluate-measure docs/cql-queries/stratifier-body-weight.yml --server http://localhost:8080/fhir | jq -rf docs/cql-queries/stratifier-body-weight.jq
```

The (shortened) output should be:

```text
Evaluate measure with canonical URL urn:uuid:dbb406a8-e72e-47a2-8ce7-c970bb964a9a on http://localhost:8080/fhir ...

"body-weight-value","body-weight-unit","count"
10.1,"kg",3
10.2,"kg",3
10.4,"kg",4
10.7,"kg",1
10.9,"kg",2
100,"kg",11
100.2,"kg",2
100.5,"kg",1
100.7,"kg",9
```

Now lets look at the three parts, the Measure YAML, the CQL library file and the jq file:

### The Measure YAML

```yaml
library: docs/cql-queries/stratifier-body-weight.cql
group:
- type: Observation
  population:
  - expression: InInitialPopulation
  stratifier:
  - code: value
    expression: QuantityValue
```

### The CQL Library File

```text
library "stratifier-body-weight"
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem loinc: 'http://loinc.org'
code "Body Weight": '29463-7' from loinc

context Patient

define InInitialPopulation:
  [Observation: "Body Weight"]

define function QuantityValue(observation Observation):
  observation.value as Quantity
```

### The jq File

```text
["body-weight-value", "body-weight-unit", "count"],
(.group[0].stratifier[0].stratum[]
  | [.extension[0].valueQuantity.value, .extension[0].valueQuantity.code, .population[0].count])
| @csv
```
