# History Type

The history type interaction retrieves the history of all resources of a particular type.

```
GET [base]/[type]/_history
```

The return content is a Bundle with type set to `history` containing the versions of the resources in question, sorted with newest versions first, and including versions of resource deletions. The history type interaction supports paging which is described in depth in the separate [paging sessions](../../api.md#paging-sessions) section.
