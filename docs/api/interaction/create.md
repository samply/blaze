# Create

The create interaction creates a new resource with a server-assigned id. If the client wishes to have control over the id of a newly submitted resource, it should use the [update](update.md) interaction instead.

```
POST [base]/[type]
```

> [!NOTE]
> Blaze runs all writes as transactions one at a time in a single total order (actual serial execution). A transaction can only start once the previous one has finished, so a large transaction blocks all smaller ones — including this create — until it completes. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.

## Conditional Create

It's possible to use conditional create in transaction or batch requests. However references to already existing resources, currently can't be resolved. If you need this feature, please vote on the issue [Implement Conditional References](https://github.com/samply/blaze/issues/433).
