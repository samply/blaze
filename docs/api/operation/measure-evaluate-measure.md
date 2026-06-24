# Operation Measure Evaluate Measure

The official documentation can be found [here][1].

* [Asynchronous Requests](../../api.md#asynchronous-requests) can be used
* [blazectl][2] supports async requests since [v0.15.0][3]
* cancellation of async requests is fully supported. the evaluation is stopped almost immediately
* the `FHIR_OPERATION_EVALUATE_MEASURE_TIMEOUT` is only applied to synchronous requests 
* [`CQL_EXPR_CACHE_SIZE`](../../deployment/environment-variables.md) can be set to enable a cache of certain CQL expressions that will speed up evaluations
* a detailed documentation how to use the $evaluate-measure API can be found [here](../../cql-queries/api.md)
* a documentation how to use $evaluate-measure via blazectl can be found [here](../../cql-queries/blazectl.md) 

## Parameters Input

In addition to the parameters defined by the R4 operation, Blaze supports the [`parameters`][4] input that was added to the operation in FHIR R5. It allows a client to pass values into the CQL evaluation, overriding the default values declared in the measure's library by name. This way a single measure can be evaluated with different inputs (e.g. thresholds, codes, date boundaries) without editing and re-publishing the library.

Because the `parameters` input is a `Parameters` resource nested in `parameter.resource`, it can **only** be supplied via `POST` with a `Parameters` body. It has no representation in the `GET` query string.

The following rules apply:

* a supplied parameter overrides the matching CQL library parameter; parameters not supplied fall back to the library default
* a parameter that appears more than once becomes a CQL `List`
* a parameter with `parameter.part` becomes a CQL `Tuple`
* supplying a value for a name the library doesn't declare returns an error
* the measurement period is derived from `periodStart`/`periodEnd` independently of CQL parameters; a supplied `Measurement Period` parameter only affects the CQL evaluation and does not change the period of the resulting `MeasureReport`

Currently the common primitive types (`boolean`, `integer`, `decimal`, `string`, `code`, `date` and `dateTime`) together with the `List`/`Tuple` rules are supported. Unsupported types return an error.

A detailed example can be found in the [CQL Evaluation API documentation](../../cql-queries/api.md#evaluation-with-parameters).

[1]: <https://hl7.org/fhir/R4/operation-measure-evaluate-measure.html>
[2]: <https://github.com/samply/blazectl>
[3]: <https://github.com/samply/blazectl/releases/tag/v0.15.0>
[4]: <https://hl7.org/fhir/R5/measure-operation-evaluate-measure.html>
