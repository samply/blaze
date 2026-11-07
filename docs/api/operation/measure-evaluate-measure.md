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
| measure     | 0..1        | string (reference) \| Measure | The measure to evaluate, either as reference or, as in FHIR R5, as `Measure` resource. Only required — and only used — when the operation is invoked on the `Measure` type rather than on a `Measure` instance. A `Measure` resource is only supported via `POST`. See [Inline Measure](#inline-measure). <Badge type="warning" text="Measure resource since 1.14.0"/> |
| reportType  | 0..1        | code               | The type of measure report: `subject`, `subject-list` or `population`. Defaults to `subject` if a `subject` is supplied, otherwise `population`. The value `subject-list` is only supported via `POST`.        |
| subject     | 0..1        | string (reference) | The subject the measure is calculated for, either a reference like `Patient/123` or a bare resource id. If omitted, the measure is calculated over all subjects.                                               |
| parameters  | 0..1        | Parameters         | Input parameters that are made available by name to the CQL evaluation, overriding the default values declared in the measure's library. Only supported via `POST`. See [Parameters Input](#parameters-input). <Badge type="warning" text="Since 1.10.0"/> |

Blaze doesn't support the `practitioner` and `lastReceivedOn` input parameters defined by the R4 operation.

## Notes

* [Asynchronous Requests](../../api.md#asynchronous-requests) can be used
* [blazectl][2] supports async requests since [v0.15.0][3]
* cancellation of async requests is fully supported. the evaluation is stopped almost immediately
* the `FHIR_OPERATION_EVALUATE_MEASURE_TIMEOUT` is only applied to synchronous requests
* [`CQL_EXPR_CACHE_SIZE`](../../deployment/environment-variables.md) can be set to enable a cache of certain CQL expressions that will speed up evaluations
* a detailed documentation how to use the \$evaluate-measure API can be found [here](../../cql-queries/api.md)
* a documentation how to use \$evaluate-measure via blazectl can be found [here](../../cql-queries/blazectl.md)

## Library Resolution

`Measure.library` references the [Library][7] resource holding the CQL used for evaluation. Only the first library canonical is considered; Blaze currently only supports measures with exactly one library. Blaze resolves it as a canonical reference, per [FHIR R4 §2.3.0.5][8]:

* `Measure.library` may carry a version, appended with `|`, e.g. `http://example.com/library-x|1.2.0`. Blaze then searches for a `Library` with matching `.url` **and** `.version`.
* Without a version, Blaze picks the Library with the highest priority among all matching `.url`, ranked by `status` (`active` first), then `version` (highest first, compared numerically per dot-separated part), then recency.
* A blank version, e.g. `http://example.com/library-x|`, is ignored and resolved like a reference without a version. Resolving the `|` syntax is equivalent to a search with a `version` query param, and blank query param values are ignored per [FHIR R4 §3.1.1.3][9].
* If no canonical match is found, Blaze falls back to resolving `Measure.library` as a literal reference (e.g. `Library/123`).
* A local reference of the form `#<id>`, e.g. `#library`, is resolved to the `Library` with that id contained in the `Measure`. <Badge type="warning" text="Since 1.14.0"/>

## Persistence

When invoked with `POST`, the `$evaluate-measure` operation persists the generated `MeasureReport` by default and returns `201 Created` with a `Location` header pointing at the stored resource. When invoked with `GET`, the report is returned inline with `200 OK` and nothing is persisted.

Persistence of the `MeasureReport` on `POST` can be disabled with the [`FHIR_OPERATION_EVALUATE_MEASURE_REPORT_PERSISTENCE`](../../deployment/environment-variables.md) environment variable. This is an operator-level decision: Blaze has no authorization, so every persisted `MeasureReport` is readable by every authenticated client. Because the `parameters` input is only available via `POST`, clients that want to pass parameters are forced onto `POST` and would otherwise get their report persisted as a side effect.

With persistence disabled (`FHIR_OPERATION_EVALUATE_MEASURE_REPORT_PERSISTENCE=false`), `POST` behaves as follows:

| reportType            | persisted        | returned                 | status |
|-----------------------|------------------|--------------------------|--------|
| `population`          | nothing          | `MeasureReport` inline   | `200`  |
| `subject`             | nothing          | `MeasureReport` inline   | `200`  |
| `subject-list`        | `List` resources | `MeasureReport` inline   | `200`  |

For `reportType=subject-list` the `List` resources holding the subject ids are still persisted, because the `MeasureReport.group.population.subjectResults` references point at them and they have to be retrievable. The `MeasureReport` itself is never persisted when the option is off.

A `MeasureReport` of an [inline measure](#inline-measure) is never persisted, regardless of this option. <Badge type="warning" text="Since 1.14.0"/>

A `Prefer: return=minimal` header is honored only on the persisting `POST` path, where it relies on the persisted resource and the `Location` header. With persistence disabled there is no persisted resource to reference, so the preference is ignored and the `MeasureReport` is always returned in the body.

## Inline Measure <Badge type="warning" text="Since 1.14.0"/>

Instead of evaluating a stored `Measure`, a client can send the `Measure` resource itself in the `measure` input parameter of a `POST [base]/Measure/$evaluate-measure` request. Although Blaze is an R4 server, where the `measure` parameter is a reference, it supports the `Measure` resource of the [R5 operation][4] in a forward-compatible way. This allows ad-hoc aggregate queries without creating `Measure` or `Library` resources first.

The following rules apply:

* the `Measure` has to have a `url`, which is used as `MeasureReport.measure`
* the `Library` holding the CQL can be contained in the `Measure` and referenced by a local reference like `#library`, or be a stored `Library` resolved as described in [Library Resolution](#library-resolution)
* a `Measure` resource can't be used on a `Measure` instance
* the `MeasureReport` is always returned inline with `200 OK` and never persisted. For `reportType=subject-list` the `List` resources holding the subject ids are still persisted, so that the references in the report resolve.

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "periodStart", "valueDate": "2000" },
    { "name": "periodEnd", "valueDate": "2030" },
    {
      "name": "measure",
      "resource": {
        "resourceType": "Measure",
        "url": "urn:uuid:6b2ab3e8-6f7d-4a4c-9a4e-4d6b8f2a7b5e",
        "status": "active",
        "subjectCodeableConcept": {
          "coding": [{ "system": "http://hl7.org/fhir/resource-types", "code": "Patient" }]
        },
        "contained": [
          {
            "resourceType": "Library",
            "id": "library",
            "status": "active",
            "type": {
              "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/library-type", "code": "logic-library" }]
            },
            "content": [{ "contentType": "text/cql", "data": "<base64 encoded CQL>" }]
          }
        ],
        "library": ["#library"],
        "scoring": {
          "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/measure-scoring", "code": "cohort" }]
        },
        "group": [
          {
            "population": [
              {
                "code": {
                  "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/measure-population", "code": "initial-population" }]
                },
                "criteria": { "language": "text/cql-identifier", "expression": "InInitialPopulation" }
              }
            ],
            "stratifier": [
              {
                "code": { "text": "gender" },
                "criteria": { "language": "text/cql-identifier", "expression": "Gender" }
              }
            ]
          }
        ]
      }
    }
  ]
}
```

## Parameters Input <Badge type="warning" text="Since 1.10.0"/>

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
[7]: <https://hl7.org/fhir/R4/library.html>
[8]: <https://hl7.org/fhir/R4/references.html#canonical>
[9]: <https://hl7.org/fhir/R4/search.html#errors>
