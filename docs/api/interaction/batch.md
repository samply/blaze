# Batch

The batch interaction allows to submit a set of actions to be performed as a single HTTP request. The semantics of the individual actions described in the `batch` Bundle are identical of the semantics of the corresponding individual request. The actions are performed in order but can be interleaved by other individual requests or actions from other batch interactions.

```
POST [base]
```

> [!NOTE]
> Each write action in a batch becomes its own transaction, and Blaze runs all transactions one at a time in a single total order (actual serial execution). A transaction can only start once the previous one has finished, so a large transaction — whether from this batch or another request — blocks all smaller ones until it completes. To keep write actions atomic and isolated as a group, use a [transaction](transaction.md) bundle instead. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.
