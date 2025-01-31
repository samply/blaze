# History Instance

The history instance interaction retrieves the history of a particular resource.

```
GET [base]/[type]/[id]/_history
```

The return content is a Bundle with type set to `history` containing the versions of the resource in question, sorted with newest versions first, and including versions of resource deletions. The history instance interaction supports paging which is described in depth in the separate [paging sessions](../../api.md#paging-sessions) section.
