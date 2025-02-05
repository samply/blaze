# FHIR Code Systems <Badge type="warning" text="unreleased"/>

Blaze supports all FHIR CodeSystem resources stored in Blaze with `content` of either `complete` or `fragment`.

## Filters

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
