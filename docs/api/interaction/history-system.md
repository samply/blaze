# History System

The history system interaction retrieves the history of all resources of the whole system.

```
GET [base]/_history
```

The return content is a Bundle with type set to `history` containing the versions of the resources in question, sorted with newest versions first, and including versions of resource deletions. The history system interaction supports paging which is described in depth in the separate [paging sessions](../../api.md#paging-sessions) section.
