# Operation \$validate-code on CodeSystem <Badge type="info" text="Feature: TERMINOLOGY_SERVICE"/> <Badge type="warning" text="Since 0.32"/>

The \$validate-code operation can be used to validate-code all codes of a CodeSystem.

```
GET [base]/CodeSystem/$validate-code
GET [base]/CodeSystem/[id]/$validate-code
```

## In Parameters

| Name            | Cardinality | Type            | Documentation                                                                                   |
|-----------------|-------------|-----------------|-------------------------------------------------------------------------------------------------|
| url             | 0..1        | uri             | A canonical reference to a code system. The code system has to be already stored on the server. | 
| codeSystem      | 0..1        | CodeSystem      | The code system is provided directly as part of the request.                                    | 
| code            | 0..1        | code            | The code that is to be validated.                                                               |
| version         | 0..1        | string          | The version of the code system, if one was provided in the source data.                         |
| display         | 0..1        | string          | The display associated with the code, if provided will be validated.                            |
| coding          | 0..1        | Coding          | A coding to validate. The system must match the specified code system.                          |
| codeableConcept | 0..1        | CodeableConcept | A full codeableConcept to validate. Only one Coding is supported.                               |
| displayLanguage | 0..1        | code            | Specifies the language to be used for description when validating the display property.         |
| tx-resource     | 0..*        | code            | Used by the Java validator.                                                                     |

## Resolution of CodeSystem Resources

More on resolution of terminology resources can be found [here](../../terminology-service/resource-resolution.md).

The official documentation can be found [here][1].

[1]: <http://hl7.org/fhir/R4/codesystem-operation-validate-code.html>
