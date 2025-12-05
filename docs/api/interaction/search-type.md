# Search Type

```
GET [base]/[type]?param1=value&...
POST [base]/[type]/_search
```

## Implemented Modifiers

The following search param modifiers are supported:

| Modifier     | Types                   | Description                                                                                                                            |
|--------------|-------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `below`      | uri                     | Tests whether the value in a resource is or is subsumed by the supplied parameter value (is-a, or hierarchical relationships).         |
| `identifier` | reference               | Tests whether the `Reference.identifier` in a resource matches the supplied parameter value.                                           |
| `:[type]`    | reference               | Tests whether the value in a resource points to a resource of the supplied parameter type (e.g., `:Patient`).                          |

See [search modifiers][4] for a complete list of modifiers

## Search Result Parameters

| Name             | Since | Description                                                     |
|------------------|-------|-----------------------------------------------------------------|
| `_sort`          |       | see [Sorting](#sorting)                                         |
| `_count`         |       | the default page size is 50 and the maximum page size is 10.000 |
| `_include`       |       | supported, except the wildcard `*`                              |
| `_revinclude`    |       | supported, except the wildcard `*`                              |
| `_summary`       | 1.0.0 | `true`, `count` and `false` are supported                       |
| `_total`         |       | `accurate` is supported                                         |
| `_elements`      |       | fully supported                                                 |
| `_contained`     |       | not supported                                                   |
| `_containedType` |       | not supported                                                   |
| `__explain`      | 1.1.0 | see [Query Plan](#query-plan)                                   |

## _profile

Search for `Resource.meta.profile` is supported using the `_profile` search param with exact match or using the `below` modifier as in `_profile:below` with major and minor version prefix match. [Semver][1] is expected for version numbers so a search value of `<url>|1` will find all versions with major version `1` and a search value of `<url>|1.2` will find all versions with major version `1` and minor version `2`. Patch versions are not supported with the `below` modifier.

## Date Search

When searching for date/time with a search parameter value without timezone like `2024` or `2024-02-16`, Blaze calculates the range of the search parameter values based on [UTC][2]. That means that a resource with a date/time value staring at `2024-01-01T00:00:00+01:00` will be not found by a search with `2024`. Please comment on [issue #1498](https://github.com/samply/blaze/issues/1498) if you like to have this situation improved.

## Geopositional Search

Blaze implements the [positional](https://hl7.org/fhir/R4/location.html#positional) search parameter `near` for resources with a geospatial position (i.e., Location). The search parameter takes a latitude, longitude, distance and unit as search parameter values in the form `longitude|latitude[|distance[|unit]]`. Defaults for `distance` and `unit` are `1` and `km`. The [Haversine formula](https://en.wikipedia.org/wiki/Haversine_formula) is used to calculate the distance between the search parameter value and the resource's location, which simplifies calculation by assuming a spherical earth and has an error of less than approximately 0.5%.

## Sorting

The special search parameter `_sort` supports the values `_id`, `_lastUpdated` and `-_lastUpdated`.

## Paging

The search-type interaction supports paging which is described in depth in the separate [paging sessions](../../api.md#paging-sessions) section.

## Patient Search Optimization

Searches including both token parameters and patient references are optimized. For Blaze to detect such a query, the search value of the token parameter has to use the syntax `[system]|[code]`, and the reference has to be an unambiguous patient reference. The following search parameter URLs are possible:

### Patient Param

Here the [`patient`][3] search param, available for multiple resource types, ensures that references have to be patient references.

```
[base]/[type]?[token-param]=[system]|[code]&patient=[id]
``` 

### Subject Param with Patient Reference

Here the `Patient/` prefix in the reference ensures that the `id` is a patient ID.

```
[base]/[type]?[token-param]=[system]|[code]&[reference-param]=Patient/[id]
```

### Query Plan <Badge type="warning" text="Since 1.1"/>

To understand how Blaze executes a search, you can request a query plan by setting the `__explain` search parameter to `true`. The server will then return an `OperationOutcome` as the first entry in the result bundle, with the query plan contained in its `diagnostics` field.

The query plan follows this format:

```
[TYPE: <type>;] SCANS(<ordering>): <scans>; SEEKS: <seeks>
```

Here's what each part means:

*   **TYPE**: (Optional) The query execution type. Currently, the only possible value is `compartment`.
*   **SCANS**: A list of search parameters that will be resolved by the more performant index scanning method.
*   **ordering**: Specifies if the scans are `ordered` (the default) or `unordered`. Unordered scans are a fallback.
*   **SEEKS**: A list of search parameters that will be resolved by index seeking.

Blaze's query optimizer generally places `token` type search parameters in `SCANS` and all other types in `SEEKS`. If a query has multiple `token` parameters, the optimizer uses internal statistics to pick the most specific one (with the smallest index segment) for the `SCANS` part to ensure the best performance. The query engine will first perform the scan and then filter the results using the seeks.

**Example 1**

A `GET` request to `/Observation?status=final&date=2025&__explain=true` will result in the following `diagnostics` string in the `OperationOutcome`:

```
SCANS: status(ordered); SEEKS: date
```

The `status` search parameter is of type `token`, while the `date` search parameter is of type `date`. Therefore, `status` is used for scanning the index. The query execution will scan the `status` index for all Observation resources with a status of `final` and then check if the `effectiveDateTime` is in `2025` for each of those resources. This allows for efficient pagination, as subsequent pages can be retrieved quickly by seeking to the correct starting point in the `status` index.

**Example 2**

A `GET` request to `/Observation?status=final&code=http://loinc.org|9843-4&__explain=true` will result in the following `diagnostics` string in the `OperationOutcome`:

```
SCANS: code(ordered); SEEKS: status
```

Both the `status` and `code` search parameters are of type `token`. However, the index segment for the LOINC code `9843-4` is much smaller than for the status `final`, because fewer Observation resources contain that LOINC code. The query execution will scan the `code` index for all Observation resources with the code `http://loinc.org|9843-4` and then check for each of those resources if the `status` is `final`. This allows for efficient pagination, as subsequent pages can be retrieved quickly by seeking to the correct starting point in the `code` index.   


[1]: <https://semver.org>
[2]: <https://en.wikipedia.org/wiki/Coordinated_Universal_Time>
[3]: <https://hl7.org/fhir/R4/searchparameter-registry.html#clinical-patient>
[4]: https://hl7.org/fhir/R4/search.html#modifiers