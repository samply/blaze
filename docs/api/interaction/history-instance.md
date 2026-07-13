# History Instance

The history instance interaction retrieves the history of a particular resource.

```
GET [base]/[type]/[id]/_history
```

The return content is a Bundle with type set to `history` containing the versions of the resource in question, sorted with newest versions first, and including versions of resource deletions. The history instance interaction supports paging which is described in depth in the separate [paging sessions](../../api.md#paging-sessions) section.

## Search Result Parameters

| Name       | Since | Description                                                         |
|------------|-------|---------------------------------------------------------------------|
| `_since`   | 1.0.0 | only include resource versions created at or after the given instant |
| `_summary` | 1.0.0 | `true`, `count` and `false` are supported                           |

## Large Histories

For histories with more than 2^31-1 entries, the data type of `Bundle.total` can no longer store the number of history entries. In that case, for numbers higher than 2^31-1, Blaze omits the `Bundle.total` value and instead uses an extension to represent the number of entries using the string data type. To stay backward compatible with existing clients and to allow downgrades to older Blaze versions, Blaze emits the extension under both the current `https://blaze-server.org/fhir` canonical and the legacy `https://samply.github.io/blaze/fhir` canonical; the legacy canonical will be removed in the next major version (v2). A bundle would look like this:

```json
{
  "resourceType": "Bundle",
  "type": "history",
  "_total": {
    "extension": [
      {
        "url": "https://blaze-server.org/fhir/StructureDefinition/grand-total",
        "valueString": "1000000000000"
      },
      {
        "url": "https://samply.github.io/blaze/fhir/StructureDefinition/grand-total",
        "valueString": "1000000000000"
      }
    ]
  }
}
```
