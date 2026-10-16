# CQL Queries with an AI Chat

An AI chat assistant that can run shell commands can answer questions about the data in Blaze for you. You ask in plain language, the assistant writes a CQL library and a Measure YAML, evaluates them with [blazectl](blazectl.md) and reads the count out of the resulting [MeasureReport](http://www.hl7.org/fhir/measurereport.html).

<CqlChat />

## What You Need

* a running Blaze server with some data, as described in [CQL Queries with blazectl](blazectl.md#run-blaze)
* [blazectl](https://github.com/samply/blazectl) and [jq](https://jqlang.org) installed on the machine the assistant runs its commands on
* an AI chat assistant that can write files and run shell commands on that machine and can reach the Blaze server over HTTP

## What the Assistant Does

For each question, the assistant goes through the same steps you would take on the command line:

1. It writes a CQL library that defines the population, for example `InInitialPopulation` for male patients, and, if the question asks for a distribution, a stratifier expression such as `BirthDecade`.
2. It writes a Measure YAML that references the library and names the population and stratifier expressions.
3. It runs `blazectl evaluate-measure`, which creates the Library and Measure resources on Blaze and executes the [$evaluate-measure](../api/operation/measure-evaluate-measure.md) operation.
4. It extracts the counts from the MeasureReport with `jq` and answers your question.

Because the CQL library and the Measure YAML are ordinary files, you can review them, rerun them yourself or keep them for later. The structure of both files is explained in [CQL Queries with blazectl](blazectl.md).
