# Batch

The batch interaction allows submitting a set of actions to be performed as a single HTTP request. The semantics of the individual actions described in the `batch` Bundle are identical of the semantics of the corresponding individual request. The actions are independent of each other, are performed in no particular order and can be interleaved by other individual requests or actions from other batch interactions.

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

## Processing Rules <Badge type="warning" text="Since 1.11.0"/>

FHIR requires that there are [no interdependencies][1] between the entries of a batch bundle: the success or failure of one action must not alter the success or failure or the resulting content of another one. Blaze relies on that rule and processes the entries concurrently. That concurrency is about submitting the actions; the transactions they create are still applied one at a time as described in the note above.

* **No order.** The actions aren't performed in the order of the bundle, and no order between any two of them is guaranteed. Two actions writing the same resource are applied in an undefined order.
* **Reads aren't separated from writes.** In a [transaction](transaction.md) bundle, the `GET` actions are executed after the writes, against the new database state. In a batch, a `GET` is just another independent action, so whether it sees a write of the same bundle is undefined. Use a transaction bundle if an action has to read what the same bundle writes.
* **Bounded concurrency.** At most 64 actions are processed at a time, and never more than half of the maximum number of in-flight transactions set by the [environment variable](../../deployment/environment-variables.md) `DB_MAX_IN_FLIGHT_TRANSACTIONS`. So a single batch can't take more than half of the places for in-flight transactions away from other clients.
* **Backpressure.** A write action rejected because that maximum is reached is retried a few times. If it still doesn't get one of the places, it keeps its `503` in the response bundle, where it then really means that the server is saturated.

None of this affects the response bundle, whose entries always keep the order of the request entries.

> [!NOTE]
> Up to version 1.10.1, the actions of a batch were processed one after another in the order of the bundle. Bundles relying on that order were already outside what FHIR guarantees, but they can behave differently now.

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

[1]: <https://hl7.org/fhir/R4/http.html#brules>
