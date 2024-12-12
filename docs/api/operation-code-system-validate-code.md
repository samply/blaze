# Operation \$validate-code on CodeSystem

> [!CAUTION]
> The operation \$validate-code on CodeSystem is currently **beta**. Only the basic functionality, described here, is implemented.

The \$validate-code operation can be used to validate-code all codes of a CodeSystem.

```
GET [base]/CodeSystem/$validate-code
GET [base]/CodeSystem/[id]/$validate-code
```

## In Parameters

| Name       | Cardinality | Type       | Documentation                                                                                   |
|------------|-------------|------------|-------------------------------------------------------------------------------------------------|
| url        | 0..1        | uri        | A canonical reference to a code system. The code system has to be already stored on the server. | 
| codeSystem | 0..1        | CodeSystem | The code system is provided directly as part of the request.                                    | 
| code       | 0..1        | code       | The code that is to be validated.                                                               |
| coding     | 0..1        | Coding     | A coding to validate. The system must match the specified code system.                          |

The official documentation can be found [here][1].

[1]: <http://hl7.org/fhir/R4/codesystem-operation-validate-code.html>
