# Search Type

```
GET [base]/[type]?param1=value&...
POST [base]/[type]/_search
```

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

### Query Plan

If the `__explain` search result parameter is set to `true`, the server returns an `OperationOutcome` as the first entry in the result bundle. That `OperationOutcome` contains a query plan in its `diagnostics` field.

The query plan has the following format:

```
[TYPE: <type>;] SCANS: <scans>; SEEKS: <seeks>
```

Where:

*   `TYPE`: (Optional) The type of query execution. The only possible value is `compartment`.
*   `SCANS`: A list of search parameters that will be resolved by index scanning.
*   `SEEKS`: A list of search parameters that will be resolved by index seeking.

Generally, `token` type search parameters are placed in `SCANS`, while others are placed in `SEEKS`. If a query has multiple `token` search parameters, the most specific one (the one with the smallest index segment) is chosen for `SCANS`, and the rest are placed in `SEEKS`. Blaze uses internal statistics to determine the size of the index segments.

A `scan` is more performant than a `seek`. The query execution will scan the index for the parameter in `SCANS` and then filter those results by seeking for the parameters in `SEEKS`.

**Example 1**

A `GET` request to `/Observation?status=final&date=2025&__explain=true` will result in the following `diagnostics` string in the `OperationOutcome`:

```
SCANS: status; SEEKS: date
```

The `status` search parameter is of type `token`, while the `date` search parameter is of type `date`. Therefore, `status` is used for scanning the index. The query execution will scan the `status` index for all Observation resources with a status of `final` and then check if the `effectiveDateTime` is in `2025` for each of those resources. This allows for efficient pagination, as subsequent pages can be retrieved quickly by seeking to the correct starting point in the `status` index.

**Example 2**

A `GET` request to `/Observation?status=final&code=http://loinc.org|9843-4&__explain=true` will result in the following `diagnostics` string in the `OperationOutcome`:

```
SCANS: code; SEEKS: status
```

Both the `status` and `code` search parameters are of type `token`. However, the index segment for the LOINC code `9843-4` is much smaller than for the status `final`, because fewer Observation resources contain that LOINC code. The query execution will scan the `code` index for all Observation resources with the code `http://loinc.org|9843-4` and then check for each of those resources if the `status` is `final`. This allows for efficient pagination, as subsequent pages can be retrieved quickly by seeking to the correct starting point in the `code` index.   


[1]: <https://semver.org>
[2]: <https://en.wikipedia.org/wiki/Coordinated_Universal_Time>
[3]: <https://hl7.org/fhir/R4/searchparameter-registry.html#clinical-patient>
