# Batch

The batch interaction allows to submit a set of actions to be performed as a single HTTP request. The semantics of the individual actions described in the `batch` Bundle are identical of the semantics of the corresponding individual request. The actions are performed in order but can be interleaved by other individual requests or actions from other batch interactions.

```
POST [base]
```

> [!NOTE]
> Each write action in a batch becomes its own transaction, and Blaze runs all transactions one at a time in a single total order (actual serial execution). A transaction can only start once the previous one has finished, so a large transaction — whether from this batch or another request — blocks all smaller ones until it completes. To keep write actions atomic and isolated as a group, use a [transaction](transaction.md) bundle instead. See [Actual Serial Execution](../../architecture.md#actual-serial-execution) for details.

The request body has to be a Bundle of type `batch`. The following methods are supported in `Bundle.entry.request.method`:

| Method   | Description                                                                                                                                                                |
|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET`    | [read](read.md), [versioned read](vread.md), [search](search-type.md), [capabilities](capabilities.md) via `metadata` and read-only operations like [Measure $evaluate-measure](../operation/measure-evaluate-measure.md). |
| `POST`   | [create](create.md), including conditional create via `Bundle.entry.request.ifNoneExist`, and operations invoked with a `Parameters` resource.                             |
| `PUT`    | [update](update.md), including version-aware updates via `Bundle.entry.request.ifMatch` and updates of not yet existing resources via `Bundle.entry.request.ifNoneMatch`.  |
| `DELETE` | [delete](delete.md) by id and [conditional delete](delete-type.md) via `DELETE [type]?[search parameters]`.                                                                |

The methods `HEAD` and `PATCH` are not supported and will result in an error entry with status `422`, unknown methods in an error entry with status `400`.

## Response

Blaze returns a `200 OK` with a Bundle of type `batch-response`. The response bundle contains one entry for each entry of the request bundle, in the same order. Each entry contains the status and further details like `ETag` value, last modified time and location under `Bundle.entry.response`. For successful reads and searches, the resource or search result Bundle is returned under `Bundle.entry.resource`.

## Handling Errors

In contrast to the [transaction](transaction.md) interaction, actions in a batch are processed independently: a failing action doesn't affect the other actions. Errors are reported per entry via `Bundle.entry.response.status` and an `OperationOutcome` under `Bundle.entry.response.outcome`, while the batch request itself still returns a `200 OK`:

```json
{
  "resourceType": "Bundle",
  "type": "batch-response",
  "entry": [
    {
      "response": {
        "status": "404",
        "outcome": {
          "resourceType": "OperationOutcome",
          "issue": [
            {
              "severity": "error",
              "code": "not-found",
              "diagnostics": "Resource `Patient/0` was not found.",
              "expression": ["Bundle.entry[0]"]
            }
          ]
        }
      }
    }
  ]
}
```
