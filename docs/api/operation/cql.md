# Operation \$cql <Badge type="info" text="Feature: ENABLE_OPERATION_CQL"/> <Badge type="warning" text="unreleased"/>

> [!CAUTION]
> The operation \$cql is currently **beta**. Only the parameters, described here, are implemented.

The $cql operation evaluates a CQL expression and returns the result. The operation has a single return parameter that can be of any type to accommodate the possible result types of a CQL expression.

This operation is defined to support evaluating CQL expressions directly via an operation..

```
POST [base]/$cql
```

## In Parameters

| Name       | Cardinality | Type               | Documentation                                                                                                                                                                                                                                                      |
|------------|-------------|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| subject    | 0..1        | string (reference) | Subject for which the expression will be evaluated. This corresponds to the context in which the expression will be evaluated and is represented as a relative FHIR id (e.g. Patient/123), which establishes both the context and context value for the evaluation |
| expression | 1..1        | string             | Expression to be evaluated. Note that this is an expression of CQL, not the text of a library with definition statements.                                                                                                                                          |
| parameters | 1..1        | string             | Expression to be evaluated. Note that this is an expression of CQL, not the text of a library with definition statements.                                                                                                                                          |
