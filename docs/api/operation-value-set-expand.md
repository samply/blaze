# Operation \$expand on ValueSet

> [!CAUTION]
> The operation \$expand on ValueSet is currently **beta**. Only the basic functionality, described here, is implemented.

The \$expand operation can be used to expand all codes of a ValueSet.

```
GET [base]/ValueSet/$expand
GET [base]/ValueSet/[id]/$expand
```

## In Parameters

| Name              | Cardinality | Type    | Documentation                                                                                                                             |
|-------------------|-------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------|
| url               | 0..1        | uri     | A canonical reference to a value set. The value set has to be already stored on the server.                                               | 
| valueSetVersion   | 0..1        | string  | The business version of the value set. If not given and multiple versions exist, an arbitrary version will be chosen.                     | 
| count             | 0..1        | integer | Paging support - how many codes should be provided in a partial page view. If count = 0, the client is asking how large the expansion is. | 
| includeDefinition | 0..1        | boolean | Controls whether the value set definition is included or excluded in value set expansions. Defaults to false.                             | 

The official documentation can be found [here][1].

[1]: <http://hl7.org/fhir/R4/valueset-operation-expand.html>
