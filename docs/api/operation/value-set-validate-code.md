# Operation \$validate-code on ValueSet <Badge type="info" text="Feature: TERMINOLOGY_SERVICE"/> <Badge type="warning" text="Since 0.32"/>

The \$validate-code operation can be used to validate-code all codes of a ValueSet.

```
GET [base]/ValueSet/$validate-code
GET [base]/ValueSet/[id]/$validate-code
```

## In Parameters

| Name            | Cardinality | Type            | Documentation                                                                                                                                                                                                                              |
|-----------------|-------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| url             | 0..1        | uri             | A canonical reference to a value set. [VCL](../../terminology-service/vcl.md) can be used. Otherwise the value set has to be already stored on the server.                                                                                 | 
| valueSet        | 0..1        | ValueSet        | The value set is provided directly as part of the request.                                                                                                                                                                                 | 
| code            | 0..1        | code            | The code that is to be validated. If a code is provided, a system must be provided.                                                                                                                                                        |
| system          | 0..1        | uri             | The system for the code that is to be validated.                                                                                                                                                                                           |
| systemVersion   | 0..1        | string          | The version of the system, if one was provided in the source data.                                                                                                                                                                         |
| display         | 0..1        | string          | The display associated with the code, if provided will be validated.                                                                                                                                                                       |
| coding          | 0..1        | Coding          | A coding to validate. The system must match the specified code system.                                                                                                                                                                     |
| codeableConcept | 0..1        | CodeableConcept | A full codeableConcept to validate. Only one Coding is supported.                                                                                                                                                                          |
| displayLanguage | 0..1        | code            | Specifies the language to be used for description when validating the display property.                                                                                                                                                    |
| inferSystem     | 0..1        | boolean         | If true, the terminology server is required to infer the system from evaluation of the value set definition. The inferSystem parameter is only to be used with the code parameter, and not with the coding nor codeableConcept parameters. |
| tx-resource     | 0..*        | code            | Used by the Java validator.                                                                                                                                                                                                                |

## Resolution of ValueSet and CodeSystem Resources

More on resolution of terminology resources can be found [here](../../terminology-service/resource-resolution.md).

The official documentation can be found [here][1].

[1]: <http://hl7.org/fhir/R4/valueset-operation-validate-code.html>
