# Operation \$graph <Badge type="info" text="Feature: ENABLE_OPERATION_GRAPH"/> <Badge type="warning" text="Since 1.14.0"/>

The \$graph operation returns a graph of resources, starting at one resource and following the links of a [GraphDefinition][2]. The GraphDefinition tells the server which other resources to return together with that resource. The result is a Bundle of type `searchset`.

```
GET  [base]/[type]/[id]/$graph?graph=[url]
POST [base]/[type]/[id]/$graph
```

Blaze supports the operation at instance level on all resource types. The type of the resource has to match the type of the start node of the GraphDefinition (see [Nodes](#nodes)). The operation has to be enabled with the environment variable [ENABLE_OPERATION_GRAPH](../../deployment/environment-variables.md#enable-operation-graph).

For `GET` requests, the `graph` parameter is taken from the query string. For `POST` requests, it's taken from the `Parameters` resource in the request body. If the `graph` parameter is additionally given in the query string of a `POST` request, it takes precedence over the one in that resource. The FHIR specification doesn't define that combination, so please use only one of both ways.

In the `Parameters` resource, the `graph` parameter has to have a string value, like `valueCanonical`, `valueUri` or `valueString`. Other values, like a `valueBoolean`, result in a `400 Bad Request`.

## In Parameters

| Name  | Cardinality | Type      | Documentation                                                                                          |
|-------|-------------|-----------|--------------------------------------------------------------------------------------------------------|
| graph | 1..1        | canonical | The canonical URL of the GraphDefinition to use. See [Selecting the GraphDefinition](#selecting-the-graphdefinition). |

## Selecting the GraphDefinition

The GraphDefinition has to be stored in Blaze. The GraphDefinition whose `url` equals the `graph` parameter is used. If several GraphDefinitions have that URL, the one with the highest priority is used. Active GraphDefinitions are preferred over draft, draft ones over retired ones. If the status is the same, the GraphDefinition with the highest version is used and after that the one created or updated last.

## GraphDefinition Structure

The GraphDefinition structure of FHIR R4 is very limited. So Blaze uses the [R5 structure of GraphDefinition][3], which consists of a start node, a list of nodes and a list of links between these nodes. Because Blaze is a FHIR R4 server, the R5 elements that don't exist in R4 are represented by [cross-version extensions][4]:

| R5 Element                    | Representation in R4                                                                                                                 |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| GraphDefinition.start         | extension `http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.start` with a `valueId`                             |
| GraphDefinition.node          | extension `http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.node` with the sub-extensions `nodeId` (`valueId`) and `type` (`valueCode`) |
| GraphDefinition.link.sourceId | extension `http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.sourceId` on the link with a `valueId`         |
| GraphDefinition.link.targetId | extension `http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.targetId` on the link with a `valueId`         |
| GraphDefinition.link.path     | the element `GraphDefinition.link.path`, which already exists in R4                                                                  |
| GraphDefinition.link.params   | extension `http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.params` on the link with a `valueString`       |

The R4 element `GraphDefinition.start` holds the type of the resource at which the graph starts. It has to be set to the type of the start node, like `Patient` for a start node of type `Patient`. The operation itself uses only the start node, but the frontend uses the `start` element to offer only GraphDefinitions which can start at the resource shown. It searches for GraphDefinitions with the [search parameter][5] `start` set to the type of that resource, `DomainResource` and `Resource`. So a GraphDefinition with a wrong or missing `start` element isn't offered by the frontend. The R4 element `GraphDefinition.link.target` isn't used.

### Nodes

Every node has a `nodeId`, which has to be unique, and a `type`. The type is either a resource type, like `Patient` or `Observation`, or one of the abstract types `Resource` and `DomainResource`. A node with type `Resource` accepts resources of all types and a node with type `DomainResource` accepts resources of all types except `Binary`, `Bundle` and `Parameters`.

The start node is the node with the id given in the start extension. The resource the operation is invoked on is the resource of the start node. So its type has to match the type of the start node, following the same rules as above. For example, a GraphDefinition with a start node of type `Patient` can only be used with `[base]/Patient/[id]/$graph`, while one with a start node of type `Resource` can be used with resources of all types. Otherwise the operation fails with a `400 Bad Request`.

### Links

A link goes from the node with the `sourceId` to the node with the `targetId`. Every link has either a `path` or `params`, not both.

#### Path Links

A path link follows the references of the source resource. The `path` is a [FHIRPath][6] expression that is evaluated on the source resource, like `encounter` on an Observation or `diagnosis.condition` on an Encounter. All values the expression returns are processed as follows:

* every reference is followed, so a path like `result` on a DiagnosticReport follows all result references,
* only relative literal references like `Encounter/0` are followed, references without a literal reference, like references with only a display, and absolute references are skipped,
* only references to resources of the type of the target node are followed, so a path `subject` going to a node of type `Patient` doesn't follow a reference to a `Group`,
* references to resources that don't exist or were deleted are skipped, and
* values that aren't references are skipped.

The FHIRPath function `resolve()` can't be used in the path. The path has to return the references itself.

#### Params Links

A params link searches for resources that refer to the source resource. The `params` are FHIR search parameters for the type of the target node, like `patient={ref}&code=http://loinc.org|8310-5` for a target node of type `Observation`. The value `{ref}` is replaced by the reference to the source resource, like `Patient/0`. All other search parameters are applied as given, so they restrict the resources found.

The following rules apply:

* exactly one search parameter has to have the value `{ref}`,
* that search parameter can't have other values in addition to `{ref}`, and
* the target node has to have a concrete resource type, not `Resource` or `DomainResource`.

### Unsupported Parts

The following parts of GraphDefinition aren't supported. Using a profile on a node or compartment rules on a link results in a `400 Bad Request`. The elements `min` and `max` of links are ignored, so a link with fewer or more target resources than allowed doesn't result in an error.

## Traversal

The result contains the resource the operation is invoked on, as the resource of the start node, and all resources reachable from it by following the links of the GraphDefinition. The following rules apply:

* Every resource occurs only once in the result, even if it's reachable in multiple ways, like an Encounter referenced by several Observations.
* If a resource is reachable through several nodes, the links of all these nodes are followed.
* Cycles, like a Condition referring to an Encounter that refers back to that Condition, are no problem.

## Response

On success the operation returns a `200 OK` together with a Bundle of type `searchset`. The Bundle contains all resources of the graph, including the resource the operation is invoked on, in a single page together with the `total` number of resources. All entries have the search mode `match`. The order of the entries isn't defined.

The following errors can occur:

| Status | Issue Code | Reason                                                                                         |
|--------|------------|------------------------------------------------------------------------------------------------|
| 400    | invalid    | The parameter `graph` is missing or has a value that isn't a string.                           |
| 400    | not-found  | No GraphDefinition with the URL given in `graph` was found.                                    |
| 400    | invalid    | The GraphDefinition is invalid, like it has no start node or a link refers to a missing node. |
| 400    | invalid    | The type of the resource doesn't match the type of the start node.                            |
| 400    | invalid    | The evaluation of the path of a link failed.                                                   |
| 404    | not-found  | The resource doesn't exist.                                                                    |
| 410    | deleted    | The resource was deleted.                                                                      |

## Notes

* the operation doesn't support paging, so the whole graph is returned in a single Bundle
* [Asynchronous Requests](../../api.md#asynchronous-requests) aren't supported
* the operation always works on a consistent database snapshot, so the result isn't affected by transactions happening while it's executed

## Examples

The following GraphDefinition starts at a patient, follows all heart rate Observations of that patient and from each of these Observations its Encounter:

```json
{
  "resourceType": "GraphDefinition",
  "url": "patient-heart-rate-encounter",
  "name": "patient-heart-rate-encounter",
  "status": "active",
  "start": "Patient",
  "extension": [
    {
      "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.start",
      "valueId": "patient"
    },
    {
      "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.node",
      "extension": [
        { "url": "nodeId", "valueId": "patient" },
        { "url": "type", "valueCode": "Patient" }
      ]
    },
    {
      "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.node",
      "extension": [
        { "url": "nodeId", "valueId": "heart-rate" },
        { "url": "type", "valueCode": "Observation" }
      ]
    },
    {
      "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.node",
      "extension": [
        { "url": "nodeId", "valueId": "encounter" },
        { "url": "type", "valueCode": "Encounter" }
      ]
    }
  ],
  "link": [
    {
      "extension": [
        {
          "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.sourceId",
          "valueId": "patient"
        },
        {
          "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.targetId",
          "valueId": "heart-rate"
        },
        {
          "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.params",
          "valueString": "patient={ref}&code=http://loinc.org|8867-4"
        }
      ]
    },
    {
      "extension": [
        {
          "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.sourceId",
          "valueId": "heart-rate"
        },
        {
          "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.link.targetId",
          "valueId": "encounter"
        }
      ],
      "path": "encounter"
    }
  ]
}
```

Store the GraphDefinition, saved in the file `graph-def.json`:

```sh
curl -s -X POST -H 'Content-Type: application/fhir+json' \
  -d @graph-def.json "http://localhost:8080/fhir/GraphDefinition"
```

Fetch the graph of the patient with the id `0`:

```sh
curl -s "http://localhost:8080/fhir/Patient/0/\$graph?graph=patient-heart-rate-encounter"
```

The same request as `POST` with a `Parameters` resource:

```sh
curl -s -X POST -H 'Content-Type: application/fhir+json' \
  -d '{"resourceType": "Parameters", "parameter": [
        {"name": "graph", "valueCanonical": "patient-heart-rate-encounter"}]}' \
  "http://localhost:8080/fhir/Patient/0/\$graph"
```

Count the returned resources by type:

```sh
curl -s "http://localhost:8080/fhir/Patient/0/\$graph?graph=patient-heart-rate-encounter" | \
  jq -r '.entry | group_by(.resource.resourceType)[] | [.[0].resource.resourceType, length] | @csv'
```

The official documentation can be found [here][1].

[1]: <https://hl7.org/fhir/R5/resource-operation-graph.html>
[2]: <https://hl7.org/fhir/R4/graphdefinition.html>
[3]: <https://hl7.org/fhir/R5/graphdefinition.html>
[4]: <https://hl7.org/fhir/R5/versions.html#extensions>
[5]: <https://hl7.org/fhir/R4/graphdefinition.html#search>
[6]: <https://hl7.org/fhir/R4/fhirpath.html>
