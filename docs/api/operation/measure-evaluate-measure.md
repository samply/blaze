# Operation \$evaluate-measure on Measure

The \$evaluate-measure operation calculates a [Measure][5] and returns the result as a [MeasureReport][6].

```
GET  [base]/Measure/$evaluate-measure
GET  [base]/Measure/[id]/$evaluate-measure
POST [base]/Measure/$evaluate-measure
POST [base]/Measure/[id]/$evaluate-measure
```

## In Parameters

| Name        | Cardinality | Type               | Documentation                                                                                                                                                                                                  |
|-------------|-------------|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| periodStart | 1..1        | date               | The start of the measurement period. In keeping with the semantics of the FHIR search date parameter, the period starts at the beginning of the period implied by the timestamp (e.g. `2014` → `2014-01-01`).  |
| periodEnd   | 1..1        | date               | The end of the measurement period. The period ends at the end of the period implied by the timestamp (e.g. `2014` → `2014-12-31`).                                                                             |
| measure     | 0..1        | string (reference) | The measure to evaluate. Only required — and only used — when the operation is invoked on the `Measure` type rather than on a `Measure` instance.                                                              |
| reportType  | 0..1        | code               | The type of measure report: `subject`, `subject-list` or `population`. Defaults to `subject` if a `subject` is supplied, otherwise `population`. The value `subject-list` is only supported via `POST`.        |
| subject     | 0..1        | string (reference) | The subject the measure is calculated for, either a reference like `Patient/123` or a bare resource id. If omitted, the measure is calculated over all subjects.                                               |
| parameters  | 0..1        | Parameters         | Input parameters that are made available by name to the CQL evaluation, overriding the default values declared in the measure's library. Only supported via `POST`. See [Parameters Input](#parameters-input). |

Blaze doesn't support the `practitioner` and `lastReceivedOn` input parameters defined by the R4 operation.

## Notes

* [Asynchronous Requests](../../api.md#asynchronous-requests) can be used
* [blazectl][2] supports async requests since [v0.15.0][3]
* cancellation of async requests is fully supported. the evaluation is stopped almost immediately
* the `FHIR_OPERATION_EVALUATE_MEASURE_TIMEOUT` is only applied to synchronous requests
* [`CQL_EXPR_CACHE_SIZE`](../../deployment/environment-variables.md) can be set to enable a cache of certain CQL expressions that will speed up evaluations
* a detailed documentation how to use the \$evaluate-measure API can be found [here](../../cql-queries/api.md)
* a documentation how to use \$evaluate-measure via blazectl can be found [here](../../cql-queries/blazectl.md)

## Parameters Input

The `parameters` input was added to the operation in FHIR R5. Although Blaze is an R4 server, it supports this [forward-compatible input][4]. It allows a client to pass values into the CQL evaluation, overriding the default values declared in the measure's library by name. This way a single measure can be evaluated with different inputs (e.g. thresholds, codes, date boundaries) without editing and re-publishing the library.

Because the `parameters` input is a `Parameters` resource nested in `parameter.resource`, it can **only** be supplied via `POST` with a `Parameters` body. It has no representation in the `GET` query string.

The following rules apply:

* a supplied parameter overrides the matching CQL library parameter; parameters not supplied fall back to the library default
* a parameter that appears more than once becomes a CQL `List`
* a parameter with `parameter.part` becomes a CQL `Tuple`
* supplying a value for a name the library doesn't declare returns an error
* the measurement period is derived from `periodStart`/`periodEnd` independently of CQL parameters; a supplied `Measurement Period` parameter only affects the CQL evaluation and does not change the period of the resulting `MeasureReport`

Currently the common primitive types (`boolean`, `integer`, `decimal`, `string`, `code`, `date` and `dateTime`) together with the `List`/`Tuple` rules are supported. Unsupported types return an error.

A detailed example can be found in the [CQL Evaluation API documentation](../../cql-queries/api.md#evaluation-with-parameters).

The official documentation can be found [here][1].

[1]: <https://hl7.org/fhir/R4/operation-measure-evaluate-measure.html>
[2]: <https://github.com/samply/blazectl>
[3]: <https://github.com/samply/blazectl/releases/tag/v0.15.0>
[4]: <https://hl7.org/fhir/R5/measure-operation-evaluate-measure.html>
[5]: <https://hl7.org/fhir/R4/measure.html>
[6]: <https://hl7.org/fhir/R4/measurereport.html>
