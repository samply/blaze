# History Instance

The history instance interaction retrieves the history of a particular resource.

```
GET [base]/[type]/[id]/_history
```

The return content is a Bundle with type set to `history` containing the versions of the resource in question, sorted with newest versions first, and including versions of resource deletions. The history instance interaction supports paging which is described in depth in the separate [paging sessions](../../api.md#paging-sessions) section.

## Large Histories

For histories with more than 2^31-1 entries, the data type of `Bundle.total` can no longer store the number of history entries. In that case, for numbers higher than 2^31-1, Blaze omits the `Bundle.total` value and instead uses an extension to represent the number of entries using the string data type. A bundle would look like this:

```json
{
  "resourceType": "Bundle",
  "type": "history",
  "_total": {
    "extension": [
      {
        "url": "https://samply.github.io/blaze/fhir/StructureDefinition/grand-total",
        "valueString": "1000000000000"
      }
    ]
  }
}
```
