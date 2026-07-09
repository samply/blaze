# Search System

```
GET [base]?param1=value&...
```

## Handling Errors

By default, Blaze ignores unknown or unsupported search parameters. If you prefer to get an error instead, you can set the `Prefer` header to `handling=strict`. In this case, Blaze will return a `400 Bad Request` with an `OperationOutcome` detailing the unknown or unsupported search parameters.

## Search Parameters <Badge type="warning" text="Since 1.11.0"/>

The following common search parameters, defined on all resources, are supported:

| Name           | Since  | Description                                |
|----------------|--------|--------------------------------------------|
| `_has`         | 1.11.0 | reverse chaining                           |
| `_id`          | 1.11.0 | logical resource id                        |
| `_lastUpdated` | 1.11.0 | date of last update                        |
| `_list`        | 1.11.0 | resources referenced by a list             |
| `_profile`     | 1.11.0 | profiles the resource claims to conform to |
| `_security`    | 1.11.0 | security labels applied to the resource    |
| `_source`      | 1.11.0 | where the resource comes from              |
| `_tag`         | 1.11.0 | tags applied to the resource               |

The resulting resources are returned in a stable but unspecified order — resources of the same type are grouped together and sorted by logical resource id within each type — matching the order of the search without any search parameters.

## Search Result Parameters

| Name          | Since  | Description                                                     |
|---------------|--------|-----------------------------------------------------------------|
| `_count`      |        | the default page size is 50 and the maximum page size is 10.000 |
| `_elements`   | 1.11.0 | fully supported                                                 |
| `_include`    | 1.11.0 | supported, except the wildcard `*`                              |
| `_revinclude` | 1.11.0 | supported, except the wildcard `*`                              |
| `_summary`    | 1.0.0  | `true`, `count` and `false` are supported                       |
| `_total`      | 1.11.0 | `accurate` is supported                                         |

## Paging

The search-system interaction supports paging which is described in depth in the separate [paging sessions](../../api.md#paging-sessions) section.
