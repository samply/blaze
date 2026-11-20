# Operation \$totals <Badge type="warning" text="Since 0.24"/>

> [!CAUTION]
> The operation \$totals is currently **experimental**. It may change in a future version without prior deprecation.

The system level \$totals operation retrieves the total number of resources by resource type.

```
GET [base]/$totals
```

The operation is formally described by the [OperationDefinition](/fhir/OperationDefinition-totals.html){target="_self"} with the canonical URL `https://blaze-server.org/fhir/OperationDefinition/totals`, defined in the [Blaze Implementation Guide](/fhir/index.html){target="_self"}.

## In Parameters

The operation has no parameters.

## Response

The response is a Parameters resource with one parameter per resource type with at least one resource. The name of the parameter is the resource type and its value is the number of resources of that type. Resource types without resources are omitted.

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "Observation",
      "valueUnsignedInt": 42
    },
    {
      "name": "Patient",
      "valueUnsignedInt": 7
    }
  ]
}
```
