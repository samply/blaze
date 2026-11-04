# Module - GraphDefinition

This module compiles [GraphDefinition][1] resources into plain data that can be used to traverse a graph of resources. It is used by the [\$graph][2] operation.

Blaze uses the R5 structure of GraphDefinition through cross-version extensions in R4.

## Validation

Compiling fails with an `incorrect` anomaly if:

* the start node is missing or doesn't refer to a node
* a node has no `nodeId`, no `type` or a `type` that isn't a resource type or one of the abstract types `Resource` and `DomainResource`
* a `nodeId` is used more than once
* a link has no `sourceId` or `targetId` or they don't refer to a node
* a link has both or neither a `path` and `params`
* a link has `params` but its target node has an abstract type, because the reverse lookup needs a concrete type
* the `params` of a link don't have exactly one parameter with the value `{ref}`, which identifies the source resource, or `{ref}` is combined with other values like in `patient={ref},foo`
* the `path` of a link is no valid FHIRPath expression
* a node has a `profile` or a link has `compartment` rules, because both are not supported

The elements `min` and `max` of links are ignored.

[1]: https://hl7.org/fhir/R5/graphdefinition.html
[2]: https://hl7.org/fhir/R5/resource-operation-graph.html
