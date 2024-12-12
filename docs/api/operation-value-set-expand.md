# Operation \$expand on ValueSet

> [!CAUTION]
> The operation \$expand on ValueSet is currently **beta**. Only the basic functionality, described here, is
> implemented.

The \$expand operation can be used to expand all codes of a ValueSet.

```
GET [base]/ValueSet/$expand
GET [base]/ValueSet/[id]/$expand
```

## In Parameters

| Name              | Cardinality | Type      | Documentation                                                                                                                                                                                 |
|-------------------|-------------|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| url               | 0..1        | uri       | A canonical reference to a value set. The value set has to be already stored on the server.                                                                                                   |
| valueSet          | 0..1        | ValueSet  | The value set is provided directly as part of the request.                                                                                                                                    |
| valueSetVersion   | 0..1        | string    | The business version of the value set. If not given and multiple versions exist, an arbitrary version will be chosen.                                                                         | 
| offset            | 0..1        | integer   | Paging support - where to start if a subset is desired. Currently only 0 is supported.                                                                                                        | 
| count             | 0..1        | integer   | Paging support - how many codes should be provided in a partial page view. If count = 0, the client is asking how large the expansion is.                                                     | 
| includeDefinition | 0..1        | boolean   | Controls whether the value set definition is included or excluded in value set expansions. Defaults to false.                                                                                 | 
| activeOnly        | 0..1        | boolean   | Controls whether inactive concepts are included or excluded in value set expansions. Defaults to true.                                                                                        | 
| excludeNested     | 0..1        | boolean   | Controls whether or not the value set expansion may nest codes or not (i.e. ValueSet.expansion.contains.contains).                                                                            | 
| system-version    | 0..*        | canonical | Specifies a version to use for a system, if the value set does not specify which one to use. The format is the same as a canonical URL: \[system\]\|\[version\] - e.g. http://loinc.org\|2.56 | 

The official documentation can be found [here][1].

[1]: <http://hl7.org/fhir/R4/valueset-operation-expand.html>
