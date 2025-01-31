# Create

The create interaction creates a new resource with a server-assigned id. If the client wishes to have control over the id of a newly submitted resource, it should use the [update](update.md) interaction instead.

```
POST [base]/[type]
```

## Conditional Create

It's currently possible to use conditional create in transaction or batch requests.
