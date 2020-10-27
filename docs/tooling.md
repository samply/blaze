# Tooling

## blazectl

Alongside Blaze, there is a command-line tool called blazectl which can be used to control a running Blaze instance. Currently, the functionality of blazectl isn't specific to Blaze itself, so it can also be used together with other FHIR® servers.

Blazectl is most suitable to upload transaction bundles to Blaze.

Download of the latest release: [blazectl v0.3.0](https://github.com/samply/blazectl/releases/tag/v0.3.0)

## Synthea Patient Generator

[Synthea Patient Generator][1] is a standard tool for generating test data which can be uploaded to Blaze.

## bbmri-fhir-gen

Another test data generator called [bbmri-fhir-gen][2] which is available from the team of the German Biobank Alliance. CQL queries in Blaze are currently tested against this dataset.

## YourKit Profiler

![](https://www.yourkit.com/images/yklogo.png)

The developers of Blaze uses the YourKit profiler to optimize performance. YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/) and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

[1]: <https://github.com/synthetichealth/synthea>
[2]: <https://github.com/samply/bbmri-fhir-gen>
