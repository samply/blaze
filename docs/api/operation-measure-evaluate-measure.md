# Operation Measure Evaluate Measure

The official documentation can be found [here][1].

* [Asynchronous Requests](../api.md#asynchronous-requests) can be used
* [blazectl][2] supports async requests since [v0.15.0][3]
* cancellation of async requests is fully supported. the evaluation is stopped almost immediately
* the `FHIR_OPERATION_EVALUATE_MEASURE_TIMEOUT` is only applied to synchronous requests 
* [`CQL_EXPR_CACHE_SIZE`](../deployment/environment-variables.md) can be set to enable a cache of certain CQL expressions that will speed up evaluations
* a detailed documentation how to use the $evaluate-measure API can be found [here](../cql-queries/api.md)
* a documentation how to use $evaluate-measure via blazectl can be found [here](../cql-queries/blazectl.md) 

[1]: <https://hl7.org/fhir/R4/operation-measure-evaluate-measure.html>
[2]: <https://github.com/samply/blazectl>
[3]: <https://github.com/samply/blazectl/releases/tag/v0.15.0>
