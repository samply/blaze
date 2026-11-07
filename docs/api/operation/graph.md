# Operation \$graph on Patient <Badge type="info" text="Feature: ENABLE_OPERATION_GRAPH"/> <Badge type="warning" text="Since 1.14.0"/>

The \$graph operation returns a graph of resources, starting at one patient and following the links of a [GraphDefinition][2]. The GraphDefinition tells the server which other resources to return together with the patient. The result is a Bundle of type `searchset`.

```
GET  [base]/Patient/[id]/$graph?graph=[url]
POST [base]/Patient/[id]/$graph?graph=[url]
```

Blaze supports the operation only at instance level on the Patient resource type. The operation has to be enabled with the environment variable [ENABLE_OPERATION_GRAPH](../../deployment/environment-variables.md#enable-operation-graph).

The `graph` parameter is only taken from the query string. A `Parameters` resource in the body of a `POST` request isn't evaluated.

## In Parameters

| Name  | Cardinality | Type | Documentation                                                                                          |
|-------|-------------|------|--------------------------------------------------------------------------------------------------------|
| graph | 1..1        | uri  | The canonical URL of the GraphDefinition to use. See [Selecting the GraphDefinition](#selecting-the-graphdefinition). |

## Selecting the GraphDefinition

The GraphDefinition has to be stored in Blaze. It's found by searching for GraphDefinition resources whose `url` equals the `graph` parameter. If several GraphDefinitions have that URL, the one with the highest priority is used. Active GraphDefinitions are preferred over draft, draft ones over retired ones. If the status is the same, the GraphDefinition with the highest version is used and after that the one created or updated last.

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

The R4 element `GraphDefinition.start` is required but not used by Blaze. Because it can't hold an id of a node, it can be marked as absent with the [data-absent-reason][5] extension, like in the [example](#examples) below. The R4 element `GraphDefinition.link.target` isn't used either.

### Nodes

Every node has a `nodeId`, which has to be unique, and a `type`. The type is either a resource type, like `Patient` or `Observation`, or one of the abstract types `Resource` and `DomainResource`. A node with type `Resource` accepts resources of all types and a node with type `DomainResource` accepts resources of all types except `Binary`, `Bundle` and `Parameters`.

The start node is the node with the id given in the start extension. The patient the operation is invoked on is the resource of the start node.

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

The traversal starts at the patient on the start node and follows all links of a node in depth-first manner. The following rules apply:

* Every resource occurs only once in the result, even if it's reachable in multiple ways, like an Encounter referenced by several Observations.
* A resource reached through several nodes is still processed on each of these nodes. So the links of all nodes a resource is reached through are followed.
* Cycles in the graph, like a Condition referring to an Encounter that refers back to that Condition, are no problem, because a resource is processed only once on each node.

## Response

On success the operation returns a `200 OK` together with a Bundle of type `searchset`. The Bundle contains all resources of the graph, including the patient itself, in a single page together with the `total` number of resources. All entries have the search mode `match`. The order of the entries isn't defined.

The following errors can occur:

| Status | Issue Code | Reason                                                                                         |
|--------|------------|------------------------------------------------------------------------------------------------|
| 400    | invalid    | The parameter `graph` is missing.                                                              |
| 400    | not-found  | No GraphDefinition with the URL given in `graph` was found.                                    |
| 400    | invalid    | The GraphDefinition is invalid, like it has no start node or a link refers to a missing node. |
| 400    | invalid    | The evaluation of the path of a link failed.                                                   |
| 404    | not-found  | The patient doesn't exist.                                                                     |
| 410    | deleted    | The patient was deleted.                                                                       |

## Notes

* the operation doesn't support paging, so the whole graph is returned in a single Bundle
* [Asynchronous Requests](../../api.md#asynchronous-requests) aren't supported
* the operation always works on a consistent database snapshot, so the result isn't affected by transactions happening while it's executed
* compiled GraphDefinitions are cached, so that a GraphDefinition doesn't have to be compiled on every request

## Examples

The following GraphDefinition starts at a patient, follows all heart rate Observations of that patient and from each of these Observations its Encounter:

```json
{
  "resourceType": "GraphDefinition",
  "url": "patient-heart-rate-encounter",
  "name": "patient-heart-rate-encounter",
  "status": "active",
  "_start": {
    "extension": [
      {
        "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
        "valueCode": "unsupported"
      }
    ]
  },
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
[5]: <https://hl7.org/fhir/R4/extension-data-absent-reason.html>
[6]: <https://hl7.org/fhir/R4/fhirpath.html>
