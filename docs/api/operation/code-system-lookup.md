# Operation \$lookup on CodeSystem <Badge type="info" text="Feature: TERMINOLOGY_SERVICE"/> <Badge type="warning" text="Since 1.7.0"/>

The \$lookup operation can be used to lookup codes of a CodeSystem.

```
GET [base]/CodeSystem/$lookup
GET [base]/CodeSystem/[id]/$lookup
```

## In Parameters

| Name        | Cardinality | Type   | Documentation                                                                     |
|-------------|-------------|--------|-----------------------------------------------------------------------------------|
| code        | 0..1        | code   | The code that is to be located. If a code is provided, a system must be provided. | 
| system      | 0..1        | uri    | The system for the code that is to be located.                                    | 
| version     | 0..1        | string | The version of the system, if one was provided in the source data.                | 
| coding      | 0..1        | Coding | A coding to look up.                                                              | 
| tx-resource | 0..*        | code   | Used by the Java validator.                                                       |

## Resolution of CodeSystem Resources

More on resolution of terminology resources can be found [here](../../terminology-service/resource-resolution.md).

The official documentation can be found [here][1].

[1]: <http://hl7.org/fhir/R4/codesystem-operation-lookup.html>
