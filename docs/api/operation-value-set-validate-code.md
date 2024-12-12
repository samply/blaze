# Operation \$validate-code on ValueSet

> [!CAUTION]
> The operation \$validate-code on ValueSet is currently **beta**. Only the basic functionality, described here, is implemented.

The \$validate-code operation can be used to validate-code all codes of a ValueSet.

```
GET [base]/ValueSet/$validate-code
GET [base]/ValueSet/[id]/$validate-code
```

## In Parameters

| Name        | Cardinality | Type     | Documentation                                                                                                                                                                                                                              |
|-------------|-------------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| url         | 0..1        | uri      | A canonical reference to a value set. The value set has to be already stored on the server.                                                                                                                                                | 
| valueSet    | 0..1        | ValueSet | The value set is provided directly as part of the request.                                                                                                                                                                                 | 
| code        | 0..1        | code     | The code that is to be validated. If a code is provided, a system must be provided.                                                                                                                                                        |
| system      | 0..1        | uri      | The system for the code that is to be validated.                                                                                                                                                                                           |
| inferSystem | 0..1        | boolean  | If true, the terminology server is required to infer the system from evaluation of the value set definition. The inferSystem parameter is only to be used with the code parameter, and not with the coding nor codeableConcept parameters. |

The official documentation can be found [here][1].

[1]: <http://hl7.org/fhir/R4/valueset-operation-validate-code.html>
