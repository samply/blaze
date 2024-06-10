# Operation Measure Evaluate Measure

The official documentation can be found [here][1].

* [Asynchronous Requests](../api.md#asynchronous-requests) can be used
* [blazectl][2] supports async requests since [v0.15.0][3]
* cancellation of async requests is fully supported. the evaluation is stopped almost immediately
* the `FHIR_OPERATION_EVALUATE_MEASURE_TIMEOUT` is only applied to synchronous requests 

[1]: <https://hl7.org/fhir/R4/operation-measure-evaluate-measure.html>
[2]: <https://github.com/samply/blazectl>
[3]: <https://github.com/samply/blazectl/releases/tag/v0.15.0>
