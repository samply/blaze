# Transaction

The transaction interaction allows submitting a set of actions to be performed inside a single transaction. Blaze supports the full set of ACID (atomicity, consistency, isolation, durability) properties. Transactions are always performed atomically and in isolation. Actions from other individual requests or actions from batch interactions will not interleave with any actions from a transaction interaction.

```
POST [base]
```

> [!NOTE]
> Blaze provides the isolation level Serializable through **actual serial execution**, running all transactions one at a time in a single, totally ordered log. A transaction can only start once the previous one has finished, so a large transaction (e.g. one that writes many thousands of resources) **blocks all smaller transactions** submitted after it until it completes. Keep transactions small to avoid delaying latency-sensitive writes. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.

The request body has to be a Bundle of type `transaction`. The following methods are supported in `Bundle.entry.request.method`:

| Method   | Description                                                                                                                                                                     |
|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET`    | [read](read.md), [versioned read](vread.md), [search](search-type.md) and other read-only interactions. They are executed after the writes, against the new database state.    |
| `POST`   | [create](create.md), including conditional create via `Bundle.entry.request.ifNoneExist`.                                                                                      |
| `PUT`    | [update](update.md), including version-aware updates via `Bundle.entry.request.ifMatch` and updates of not yet existing resources via `Bundle.entry.request.ifNoneMatch`.      |
| `DELETE` | [delete](delete.md) by id, [conditional delete](delete-type.md) via `DELETE [type]?[search parameters]` and [delete history version](delete-history-version.md).                |

The methods `HEAD` and `PATCH` are not supported and will result in a `422 Unprocessable Entity`, unknown methods in a `400 Bad Request`, each with an `OperationOutcome`. Other writes, like operations, searches via `POST` or [delete history](delete-history.md), are only supported in [batches](batch.md) and will result in a `422 Unprocessable Entity` in transactions.

## Processing Rules 

References in transaction bundles are resolved according to [Resolving references in Bundles][1]. Especially absolute URIs like URNs and URLs can be used as well as relative references in entries with absolute RESTful fullUrls.

## Response

On success, Blaze returns a `200 OK` with a Bundle of type `transaction-response`. The response bundle contains one entry for each entry of the request bundle, in the same order. Each entry contains the status, `ETag` value, last modified time and — for newly created resources — the location under `Bundle.entry.response`:

```json
{
  "resourceType": "Bundle",
  "type": "transaction-response",
  "entry": [
    {
      "response": {
        "status": "201",
        "location": "[base]/Patient/DD7MLX6H7OGJN7SD/_history/23",
        "etag": "W/\"23\"",
        "lastModified": "2025-06-24T09:03:22Z"
      }
    }
  ]
}
```

By default, response entries of writes don't contain the resources themselves. Setting the `Prefer` header to `return=representation` will return the created or updated resources under `Bundle.entry.resource`.

## Handling Errors

Transactions are processed atomically: if any entry fails, no changes are applied at all. In that case Blaze returns an error status matching the failed entry (e.g. `409 Conflict` on a referential integrity violation or `412 Precondition Failed` on a failed precondition) with a single `OperationOutcome` detailing the error. For errors detected while validating the bundle, the offending entry is given in `OperationOutcome.issue.expression`, e.g. `Bundle.entry[0].request.url`.

A transaction can act on each resource only once. The only exception are [delete history version](delete-history-version.md) entries, which can delete several versions of the same resource. Two entries acting on the same resource, like an update and a delete of `Patient/0`, result in a `400 Bad Request`. Its `OperationOutcome` names both actions in `issue.diagnostics` and gives both entries in `issue.expression`, e.g. `Bundle.entry[0]` and `Bundle.entry[1]`, each in the order they appear in the Bundle. If the conflict only shows once a conditional create or conditional delete is resolved to a resource, the transaction fails with a `409 Conflict` instead, without the entries.

## Conditional Create

It's possible to use conditional create in transaction requests. However, references to already existing resources currently can't be resolved. If you need this feature, please vote on the issue [Implement Conditional References](https://github.com/samply/blaze/issues/433).

[1]: <https://hl7.org/fhir/bundle.html#references>
