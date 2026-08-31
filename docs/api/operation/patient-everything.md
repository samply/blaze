# Operation \$everything on Patient <Badge type="warning" text="Since 0.22"/>

The \$everything operation returns all resources of the [patient compartment][2] of one patient, including the Patient resource itself and all supporting resources referenced from compartment resources. The result is a Bundle of type `searchset`.

```
GET  [base]/Patient/[id]/$everything
POST [base]/Patient/[id]/$everything
```

Blaze supports the operation only at instance level. The type level variant `[base]/Patient/$everything` isn't implemented.

For `GET` requests, the in parameters are taken from the query string. For `POST` requests, they are taken from the `Parameters` resource in the request body. <Badge type="warning" text="Since 1.12.0"/> Parameters that are additionally given in the query string of a `POST` request take precedence over the ones in that resource. The FHIR specification doesn't define that combination, so please use only one of both ways.

In the `Parameters` resource, each parameter has to use the value type given in the table below, so `valueDate` for `start` and `end`, `valueInstant` for `_since` and `valueInteger` for `_count`. A parameter with an invalid value or a different type results in a `400 Bad Request`. In the query string, only invalid `start` and `end` values are rejected, invalid `_since` and `_count` values are ignored.

## In Parameters

| Name    | Cardinality | Type    | Documentation                                                                                                                                                                          |
|---------|-------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| start   | 0..1        | date    | The lower bound of the date range the returned resources are filtered by. See [Date Filtering](#date-filtering).                                                                          |
| end     | 0..1        | date    | The upper bound of the date range the returned resources are filtered by. See [Date Filtering](#date-filtering).                                                                          |
| \_since | 0..1        | instant | Only resources that changed after that instant are returned. See [Filtering by Change Time](#filtering-by-change-time). <Badge type="warning" text="Since 1.2.0"/>                        |
| \_count | 0..1        | integer | The number of resources per page. Enables [paging](#paging). Values greater than 10,000 are capped at 10,000. A negative value is rejected in a `Parameters` resource and ignored in the query string. Without that parameter all resources are returned in a single Bundle. |

Blaze doesn't support the `_type` in parameter defined by the R4 operation. Using it results in a `400 Bad Request` with the issue code `not-supported`, so that a client doesn't get back more resources than it asked for. <Badge type="warning" text="Since 1.12.0"/> Params that are not part of the operation at all, like `_summary` or `_elements`, are ignored.

## Returned Resources

The returned Bundle contains:

* the Patient resource itself,
* all resources of the patient compartment as defined by the [Patient CompartmentDefinition][2], and
* all supporting resources, which are resources of types outside the patient compartment, like `Practitioner`, `Organization`, `Medication`, `Location`, `Device` or `Substance`, that are referenced by one of the compartment resources.

The following rules apply:

* Supporting resources are only resolved one level deep. References of supporting resources themselves are not followed.
* Infrastructure resource types like `Bundle`, `CapabilityStatement`, `OperationDefinition`, `SearchParameter`, `Subscription` or `TestScript` are never returned, even if a compartment resource references them.
* Other Patient resources that reference the patient via `Patient.link` are part of the patient compartment and are returned. Their own compartment resources are not returned, because the compartment isn't traversed recursively.
* Every resource occurs only once, even if it's reachable in multiple ways, like a `Medication` referenced by several `MedicationAdministration` resources.
* All entries have the search mode `match`, supporting resources included.

## Date Filtering

The in parameters `start` and `end` restrict the returned resources to a date range. The filtering is done with the [clinical-date][3] search parameter, using the usual FHIR date search semantics: `start` behaves like the prefix `ge` and `end` behaves like the prefix `le`. For resources with a period or a range, the whole period is taken into account. So a resource matches `start` if its date ends at or after `start` and it matches `end` if its date starts at or before `end`.

Both parameters take a FHIR date, so the precisions year (`2024`), year-month (`2024-03`) and date (`2024-03-15`) are allowed. A value that isn't a valid FHIR date results in a `400 Bad Request`.

Only resource types that are part of the base of the clinical-date search parameter are filtered:

| Resource Type       | Element used for Filtering |
|---------------------|----------------------------|
| AllergyIntolerance  | recordedDate               |
| CarePlan            | period                     |
| CareTeam            | period                     |
| ClinicalImpression  | date                       |
| Composition         | date                       |
| Consent             | dateTime                   |
| DiagnosticReport    | effective                  |
| Encounter           | period                     |
| EpisodeOfCare       | period                     |
| FamilyMemberHistory | date                       |
| Flag                | period                     |
| Immunization        | occurrence                 |
| List                | date                       |
| Observation         | effective                  |
| Procedure           | performed                  |
| RiskAssessment      | occurrence (as dateTime)   |
| SupplyRequest       | authoredOn                 |

Resources of all other types, like `MedicationAdministration`, `Specimen` or the supporting resources, are returned independently of `start` and `end`. The Patient resource itself is always returned.

## Filtering by Change Time <Badge type="warning" text="Since 1.2.0"/>

The `_since` parameter restricts the result to resources whose current version was created by a transaction that happened after the given instant. It takes an instant with a time zone offset like `2024-03-15T10:00:00Z` or `2024-03-15T11:00:00+01:00`. In the query string, a value that can't be parsed is ignored silently. In a `Parameters` resource, it results in a `400 Bad Request`.

The Patient resource itself is always returned, even if it didn't change after the given instant.

Because only current versions are returned, resources that were deleted after the given instant don't show up in the result. So `_since` can't be used to synchronize deletions.

## Paging

Without the `_count` parameter, all resources are returned in a single Bundle that also contains the `total` number of resources. Because the whole compartment has to be materialized in that case, the operation is limited to 10,000 resources. If the patient compartment contains more resources, the request fails with a `409 Conflict` and the issue code `too-costly`.

With the `_count` parameter, paging is used. In that case:

* each page contains at most `_count` resources,
* the Bundle contains a `next` link as long as further resources exist,
* the Bundle doesn't contain a `total`, because determining it would require traversing the whole compartment, and
* there is no limit on the total number of resources returned by following the next links.

Next links point to `[base]/Patient/[id]/__everything-page/[page-id]` and form a [paging session](../../api.md#paging-sessions) that operates on a stable database snapshot. The other in parameters are encoded into the page id, so they don't have to be repeated when following a next link.

## Response

On success the operation returns a `200 OK` together with a Bundle of type `searchset`. The following errors can occur:

| Status | Issue Code | Reason                                                                       |
|--------|------------|------------------------------------------------------------------------------|
| 400    | invalid    | A parameter has an invalid value, like a `start` that is no valid FHIR date. |
| 400    | not-supported | The unsupported param `_type` was used.                                   |
| 404    | not-found  | The patient doesn't exist.                                                   |
| 409    | too-costly | The patient compartment contains more than 10,000 resources and `_count` wasn't used. |
| 410    | deleted    | The patient was deleted.                                                     |

## Notes

* [Asynchronous Requests](../../api.md#asynchronous-requests) aren't supported
* the operation always works on a consistent database snapshot, so the result isn't affected by transactions happening while it's executed

## Examples

Fetch everything of the patient with the id `0`:

```sh
curl -s "http://localhost:8080/fhir/Patient/0/\$everything"
```

Fetch everything of that patient that has a clinical date in the year 2024, using pages of 500 resources:

```sh
curl -s "http://localhost:8080/fhir/Patient/0/\$everything?start=2024&end=2024&_count=500"
```

The same request as `POST` with a `Parameters` resource:

```sh
curl -s -X POST -H 'Content-Type: application/fhir+json' \
  -d '{"resourceType": "Parameters", "parameter": [
        {"name": "start", "valueDate": "2024"},
        {"name": "end", "valueDate": "2024"},
        {"name": "_count", "valueInteger": 500}]}' \
  "http://localhost:8080/fhir/Patient/0/\$everything"
```

The `next` link of the returned Bundle can be followed to fetch the remaining pages:

```sh
curl -s "http://localhost:8080/fhir/Patient/0/\$everything?_count=500" | \
  jq -r '.link[] | select(.relation == "next") | .url'
```

The official documentation can be found [here][1].

[1]: <https://www.hl7.org/fhir/R4/operation-patient-everything.html>
[2]: <https://www.hl7.org/fhir/R4/compartmentdefinition-patient.html>
[3]: <http://hl7.org/fhir/R4/searchparameter-registry.html#clinical-date>
