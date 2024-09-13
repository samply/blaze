# FHIR RESTful API

Blaze exposes a [FHIR RESTful API][1] under the default context path of `/fhir`. The [CapabilityStatement][2] exposed under `/fhir/metadata` can be used to discover the capabilities of Blaze. Everything stated there can be considered to be implemented correctly. If you find any discrepancies, please open an [issue][3]. 

## Interactions

### Read

```
GET [base]/[type]/[id]
```

### Versioned Read

```
GET [base]/[type]/[id]/_history/[vid]
```

### Update

The update interaction creates a new current version for an existing resource or creates an initial version if no resource already exists for the given id.

```
PUT [base]/[type]/[id]
```

Blaze keeps track over the history of all updates of each resource. However if the content of the resource update is equal to the current version of the resource, no new history entry is created. Usually such identical content updates will only cost a very small amount of transaction handling storage but no additional resource or index storage.

### Conditional Update

Conditional update interaction will be implemented in the future. Please see issue [#361](https://github.com/samply/blaze/issues/361) for more information.

### Patch

The patch interaction isn't implemented yet. Please open an [issue][3] if you need patch.

### Conditional Patch

The conditional patch interaction isn't implemented yet. Please open an [issue][3] if you need patch.

### Delete

The delete interaction removes an existing resource.

```
DELETE [base]/[type]/[id]
```

Because Blaze keeps a history of all resource versions, the version of the resource past the deletion will be still accessible in it's [history](#history-instance). However past versions can be deleted themself via the [delete history](#delete-history) interaction. 

By default Blaze enforces referential integrity while deleting resources. So resources that are referred by other resources can't be deleted without deleting the other resources first. That behaviour can be changed by setting the [environment variable](deployment/environment-variables.md) `ENFORCE_REFERENTIAL_INTEGRITY` to `false`.

### Delete History

> [!CAUTION]
> The delete history interaction is trial use in the unreleased next version of FHIR. So it is subject to change. Please use it with care.

The delete history interaction removes all versions of the resource except the current version. 

```
DELETE [base]/[type]/[id]/_history
```

Deleting the history of a resource means that the historical versions of that resource can no longer be accessed. Subsequent [versioned reads](#versioned-read) of that historical versions will return a `404 Not Found`. Versions are also removed from the [type history](#history-type) and [system history](#history-system). Only active [paging sessions](#paging-sessions) will be able to access the deleted versions for a limited amount of time.

> [!NOTE]
> Due to stability concerns, there is a fix limit of 100,000 versions that can be deleted by this interaction. In case more than 100,000 versions exist, an OperationOutcome with code `too-costly` is returned. Currently there is no way to delete a history with more than 100,000 versions. Please open an [issue][3] if you need to delete larger histories.

### History Instance

The history instance interaction retrieves the history of a particular resource.

```
GET [base]/[type]/[id]/_history
```

The return content is a Bundle with type set to `history` containing the versions of the resource in question, sorted with newest versions first, and including versions of resource deletions. The history instance interaction supports paging which is described in depth in the separate [paging sessions](#paging-sessions) section.

### Create

The create interaction creates a new resource with a server-assigned id. If the client wishes to have control over the id of a newly submitted resource, it should use the [update](#update) interaction instead.

```
POST [base]/[type]
```

### Conditional Create

It's currently possible to use conditional create in transaction or batch requests.

### Search Type

```
GET [base]/[type]?param1=value&...
POST [base]/[type]/_search
```

#### _profile

Search for `Resource.meta.profile` is supported using the `_profile` search param with exact match or using the `below` modifier as in `_profile:below` with major and minor version prefix match. [Semver][6] is expected for version numbers so a search value of `<url>|1` will find all versions with major version `1` and a search value of `<url>|1.2` will find all versions with major version `1` and minor version `2`. Patch versions are not supported with the `below` modifier.

#### Date Search

When searching for date/time with a search parameter value without timezone like `2024` or `2024-02-16`, Blaze calculates the range of the search parameter values based on [UTC][7]. That means that a resource with a date/time value staring at `2024-01-01T00:00:00+01:00` will be not found by a search with `2024`. Please comment on [issue #1498](https://github.com/samply/blaze/issues/1498) if you like to have this situation improved.

#### Sorting

The special search parameter `_sort` supports the values `_id`, `_lastUpdated` and `-_lastUpdated`.

#### Paging

The search-type interaction supports paging which is described in depth in the separate [paging sessions](#paging-sessions) section.

### Conditional Delete

The conditional delete operation allows to delete all resources matching certain criteria. The same search parameter as in the search type interaction can be used. The search is always strict, so it will fail on any unknown search parameter.

```
DELETE [base]/[type]?[search parameters]
```

By default, the delete is only performed if one resource matches. However it's possible to allow deleting multiple resources by setting the [environment variable](deployment/environment-variables.md) `ALLOW_MULTIPLE_DELETE` to `true`.

> [!NOTE]
> Due to stability concerns, there is a fix limit of 10,000 resources that can be deleted by this interaction. In case more than 10,000 resources match, an OperationOutcome with code `too-costly` is returned.

The successful response will have the status code `204 No Content` with no payload by default. However it's possible to specify a return preference of `OperationOutcome` by setting the `Prefer` header to `return=OperationOutcome`. In this case a success OperationOutcome with a diagnostic of the number of deleted resources is returned.

#### Example

```json 
{
  "resourceType": "OperationOutcome",
  "issue": [
    {
      "severity": "success",
      "code": "success",
      "diagnostics": "Successfully deleted 120 Provenances."
    }
  ]
}
```

### History Type

The history type interaction retrieves the history of all resources of a particular type.

```
GET [base]/[type]/_history
```

The return content is a Bundle with type set to `history` containing the versions of the resources in question, sorted with newest versions first, and including versions of resource deletions. The history type interaction supports paging which is described in depth in the separate [paging sessions](#paging-sessions) section.

### Capabilities

Get the capability statement for Blaze. Blaze supports filtering the capability statement by `_elements`. For more information, see: [FHIR - RESTful API - Capabilities][5]

### Batch

The batch interaction allows to submit a set of actions to be performed as a single HTTP request. The semantics of the individual actions described in the `batch` Bundle are identical of the semantics of the corresponding individual request. The actions are performed in order but can be interleaved by other individual requests or actions from other batch interactions. 

```
POST [base]
```

### Transaction

The transaction interaction allows to submit a set of actions to be performed inside a single transaction. Blaze supports the full set of ACID (atomicity, consistency, isolation, durability) properties. Transactions are always performed atomically and in isolation. Actions from other individual requests or actions from batch interactions will not interleave with any actions from a transaction interaction.  

```
POST [base]
```

### History System

The history system interaction retrieves the history of all resources of the whole system.

```
GET [base]/_history
```

The return content is a Bundle with type set to `history` containing the versions of the resources in question, sorted with newest versions first, and including versions of resource deletions. The history system interaction supports paging which is described in depth in the separate [paging sessions](#paging-sessions) section.

## Operations

The following Operations are implemented:

* [$graphql](http://hl7.org/fhir/resource-operation-graphql.html)
* [Measure $evaluate-measure](api/operation-measure-evaluate-measure.md)
* [Patient $everything](api/operation-patient-everything.md)

## Asynchronous Requests

Some requests like $evaluate-measure or complex FHIR searches with `_summary=count` can take longer as a typical HTTP request response cycle should be open. Typical HTTP request timeouts from client and intermediates are 30 seconds. Requests that take longer than that would require special handling. In oder to overcome that synchronous request handling, FHIR specifies [Asynchronous Request Patterns](http://hl7.org/fhir/R5/async.html).

Blaze implements the [Asynchronous Interaction Request Pattern][8] from FHIR R5.

### Example FHIR Search Request

To initiate a sync request, the HTTP header `Prefer` has to set to `respond-async`:

```sh
curl -svH 'Prefer: respond-async' "http://localhost:8080/fhir/Observation?code=http://loinc.org|8310-5&_summary=count" 
```

The response will look like this:

```text
HTTP/1.1 202 Accepted
Content-Location: http://localhost:8080/fhir/__async-status/DD7MLX6H7OGJN7SD
```

The status code 202 indicates that the request was accepted and will continue to be processed in the background. The `Content-Location` header contains an opaque URL to a status endpoint.

> [!NOTE]
> Please be aware that Blaze will respond synchronously if the response is available in time or the async handling isn't implemented yet. Clients always have to be able to handle synchronous responses as well.  

Polling that status endpoint:

```sh
curl -svH 'Accept: application/fhir+json' "http://localhost:8080/fhir/__async-status/DD7MLX6H7OGJN7SD" 
```

will either result in a intermediate result like this:

```text
HTTP/1.1 202 Accepted
X-Progress: in-progress
```

or in the final response:

```text
HTTP/1.1 200 OK
Content-Type: application/fhir+json;charset=utf-8
Content-Length: 412
```

The response itself will be a Bundle of type `batch-response`:

```json
{
  "resourceType": "Bundle",
  "id": "DD7MLX6JYN54OHKZ",
  "type": "batch-response",
  "entry": [
    {
      "response": {
        "status": "200"
      },
      "resource": {
        "resourceType": "Bundle",
        "id": "DD7MLX6JZHGD5YSA",
        "type": "searchset",
        "total": 1689,
        "link": [
          {
            "relation": "self",
            "url": "http://localhost:8080/fhir/Observation?code=http%3A%2F%2Floinc.org%7C8310-5&_summary=count&_count=50"
          }
        ]
      }
    }
  ]
}
```

### Cancelling an Async Request

Async requests can be cancelled before they are completed:

```sh
curl -svXDELETE "http://localhost:8080/fhir/__async-status/DD7MLX6H7OGJN7SD"
```

## Paging Sessions

Interactions and operations that return a large list of resources support paging via Bundle resources. The various Bundle resources are interlinked via the next link. The process of retrieving a part of or all Bundle resources (pages) of a large response forms a paging session. Paging sessions have the following properties: 

### Stable

Paging sessions operate on a stable database snapshot. Next links will point to custom paging session endpoints. The endpoints will expire after for 4 hours in order to constrain the usage of a paging session. That also means that clients which have access to a paging session, will be able to access deleted and changed resources for up to 4 hours.

### Expire

Paging sessions will expire after 4 hours without activity. Activities are requesting the first or next page.

### Fast

Paging sessions don't require initial setup time and show constant costs per page in most cases. In fact paging sessions don't require book keeping at server side at all. Their state is solely communicated via link URLs. For search requests with more than one parameter, page costs can vary because of internal query handling.

### Encrypted

The variable part of paging URLs is encrypted to ensure confidentiality and integrity of the paging parameters. Confidentiality is important in case some of the original query parameters contain sensitive information. To mitigate the risk of exposing this data, FHIR searches are often executed via POST requests, which helps prevent sensitive information from being logged in URLs. Consequently, it is essential that paging URLs do not reveal any confidential data. Integrity is important, because it should not be possible to manipulate the paging URL in order to access a different paging session.

#### Encryption Key Management

<dl>
  <dt>Key Rotation</dt>
  <dd>Encryption keys are rotated every two hours. Each key is valid for a maximum of four hours, with a total of three keys stored at any time.</dd>
  <dt>Storage</dt>
  <dd>Currently, encryption keys are stored in plain text within the admin database. While these keys are not accessible via an API, they are also not encrypted with an external key encryption method.</dd>
  <dt>Future Improvements</dt>
  <dd>Implementing external key encryption is feasible but would require additional infrastructure. If you believe that key encryption is necessary, please open an issue for further discussion.</dd>
</dl>

## Absolute URLs

Blaze has to generate absolute URLs of its own in links and Location headers. By default Blaze assumes to be accessible under `http://localhost:8080`. The [environment variable](deployment/environment-variables.md) `BASE_URL` can be used to change this.

Besides the static `BASE_URL` setting, Blaze also respects the reverse proxy headers X-Forwarded-Host, X-Forwarded-Proto and [Forwarded][4] to generate its base URL dynamically.

[1]: <https://www.hl7.org/fhir/http.html>
[2]: <https://www.hl7.org/fhir/capabilitystatement.html>
[3]: <https://github.com/samply/blaze/issues>
[4]: <https://datatracker.ietf.org/doc/html/rfc7239>
[5]: <https://hl7.org/fhir/http.html#capabilities>
[6]: <https://semver.org>
[7]: <https://en.wikipedia.org/wiki/Coordinated_Universal_Time>
[8]: <http://hl7.org/fhir/R5/async-bundle.html>
