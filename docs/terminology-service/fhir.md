# FHIR Code Systems <Badge type="warning" text="Since 0.32"/>

Blaze supports all FHIR CodeSystem resources stored in Blaze with `content` of either `complete` or `fragment`.

## Filters

The following filters are supported.

| Property | Operators           | Values     |
|----------|---------------------|------------|
| concept  | is-a, descendent-of | code       |
| parent   | exists              | true/false |
| parent   | =                   | code       |
| parent   | regex               | *regex*    |
| child    | exists              | true/false |
| child    | =                   | code       |
| child    | regex               | *regex*    |
| *any*    | exists              | true/false |
| *any*    | =                   | *any*      |
| *any*    | regex               | *regex*    |

## Resolution of CodeSystem Resources

More on resolution of terminology resources can be found [here](resource-resolution.md).
