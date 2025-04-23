# Search Type

```
GET [base]/[type]?param1=value&...
POST [base]/[type]/_search
```

## Search Result Parameters

| Name             | Description                                                                                                                                    |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `_sort`          | see Sorting                                                                                                                                    |
| `_count`         | the default page size is 50 and the maximum page size is 10.000                                                                                |
| `_include`       | supported, except the wildcard `*`                                                                                                             |
| `_revinclude`    | supported, except the wildcard `*`                                                                                                             |
| `_summary`       | `true`, `data`, `count` and `false` is supported for CodeSystem and ValueSet resources while `count` is supported for all other resource types |
| `_total`         | `accurate` is supported                                                                                                                        |
| `_elements`      | fully supported                                                                                                                                |
| `_contained`     | not supported                                                                                                                                  |
| `_containedType` | not supported                                                                                                                                  |

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

[1]: <https://semver.org>
[2]: <https://en.wikipedia.org/wiki/Coordinated_Universal_Time>
[3]: <https://hl7.org/fhir/R4/searchparameter-registry.html#clinical-patient>
