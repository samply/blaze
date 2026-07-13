# Transaction

The transaction interaction allows to submit a set of actions to be performed inside a single transaction. Blaze supports the full set of ACID (atomicity, consistency, isolation, durability) properties. Transactions are always performed atomically and in isolation. Actions from other individual requests or actions from batch interactions will not interleave with any actions from a transaction interaction.

```
POST [base]
```

> [!NOTE]
> Blaze provides the isolation level Serializable through **actual serial execution**, running all transactions one at a time in a single, totally ordered log. A transaction can only start once the previous one has finished, so a large transaction (e.g. one that writes many thousands of resources) **blocks all smaller transactions** submitted after it until it completes. Keep transactions small to avoid delaying latency-sensitive writes. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.

## Processing Rules 

References in transaction bundles are resolved according to [Resolving references in Bundles][1]. Especially absolute URIs like URNs and URLs can be used as well as relative references in entries with absolute RESTful fullUrls.

## Conditional Create

It's possible to use conditional create in transaction requests. However references to already existing resources, currently can't be resolved. If you need this feature, please vote on the issue [Implement Conditional References](https://github.com/samply/blaze/issues/433).

[1]: <https://hl7.org/fhir/bundle.html#references>
