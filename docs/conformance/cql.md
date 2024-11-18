# CQL Conformance

## Implementation State of ELM Forms

The section numbers refer to the documentation of the [ELM Specification](https://cql.hl7.org/04-logicalspecification.html) v1.5.1. Please note that CQL itself translates to ELM, but doesn't use every ELM feature. Because Blaze implements ELM and not CQL directly, the documentation is about the ELM forms and not about the CQL forms. A clear view about the CQL coverage of Blaze is not available yet.

### 1. Simple Values

| Num  | Group   | Expression | State                                          | Notes |
|------|---------|------------|------------------------------------------------|-------|
| 1.1. | Literal | !          | only `Boolean`, `Integer`, `Decimal`, `String` |       |

### 2. Structured Values
   
| Num  | Group    | Expression | State            | Notes                       |
|------|----------|------------|------------------|-----------------------------|
| 2.1. | Tuple    | ✓          |                  |                             |
| 2.2. | Instance | ✓          |                  |                             |
| 2.3. | Property | !          | no full FHIRPath | FHIRPath is not used by CQL |

### 3. Clinical Values

| Num   | Group         | Expression | State                    | Notes |
|-------|---------------|------------|--------------------------|-------|
| 3.1.  | Code          | ✓          |                          |       |
| 3.2.  | CodeDef       | ✓          |                          |       |
| 3.3.  | CodeRef       | !          | only inside same library |       |
| 3.4.  | CodeSystemDef | ✓          |                          |       |
| 3.5.  | CodeSystemRef | !          | only inside same library |       |
| 3.6.  | Concept       | ✓          |                          |       |
| 3.7.  | ConceptDef    | ✓          |                          |       |
| 3.8.  | ConceptRef    | ✓          |                          |       |
| 3.9.  | Quantity      | ✓          |                          |       |
| 3.10. | Ratio         | ✓          |                          |       |
| 3.11. | ValueSetDef   | ✓          |                          |       |
| 3.12. | ValueSetRef   | ✓          |                          |       |

### 4. Type Specifiers

| Num  | Group                 | Expression | State | Notes |
|------|-----------------------|------------|-------|-------|
| 4.1. | TypeSpecifier         | ✓          |
| 4.2. | NamedTypeSpecifier    | ✓          |
| 4.3. | IntervalTypeSpecifier | ✓          |
| 4.4. | ListTypeSpecifier     | ✓          |
| 4.5. | TupleTypeSpecifier    | ✓          |
| 4.6. | ChoiceTypeSpecifier   | ✓          |

### 5. Libraries

| Num  | Group               | Expression | State                                | Notes |
|------|---------------------|------------|--------------------------------------|-------|
| 5.1. | Library             | ✓          |                                      |       |
| 5.2. | IncludeDef          | !          | no custom includes, only FHIRHelpers |       |
| 5.3. | VersionedIdentifier | ✗          |                                      |       |
| 5.4. | ContextDef          | ✗          |                                      |       |


### 6. Data Model

| Num           | Group | Expression   | State | Notes |
|---------------|-------|--------------|-------|-------|
| 6.1. UsingDef | !     | only FHIR R4 |       |       |

### 7. Parameters

| Num  | Group        | Expression | State | Notes |
|------|--------------|------------|-------|-------|
| 7.1. | ParameterDef | ✓          |
| 7.2. | ParameterRef | ✓          |

### 8. Expressions

| Num  | Group               | Expression | State | Notes |
|------|---------------------|------------|-------|-------|
| 8.1. | Expression          | ✓          |
| 8.2. | OperatorExpression  | ✓          |
| 8.3. | UnaryExpression     | ✓          |
| 8.4. | BinaryExpression    | ✓          |
| 8.5. | TernaryExpression   | ✓          |
| 8.6. | NaryExpression      | ✓          |
| 8.7. | AggregateExpression | ✓          |

### 9. Reusing Logic

| Num  | Group         | Expression | State                                       | Notes |
|------|---------------|------------|---------------------------------------------|-------|
| 9.1. | ExpressionDef | ✓          |                                             |       |
| 9.2. | ExpressionRef | !          | only inside same library                    |       |
| 9.3. | FunctionDef   | ✓          |                                             |       |
| 9.4. | FunctionRef   | !          | hard coded implementation of some functions |       |
| 9.5. | OperandRef    | ✓          |                                             |       |

### 10. Queries

| Num    | Group              | Expression | State              | Notes |
|--------|--------------------|------------|--------------------|-------|
| 10.1.  | Query              | ✓          |                    |       |
| 10.2.  | AliasedQuerySource | !          | only single source |       |
| 10.3.  | AliasRef           | ✓          |                    |       |
| 10.4.  | ByColumn           | ✓          |                    |       |
| 10.5.  | ByDirection        | ✓          |                    |       |
| 10.6.  | ByExpression       | ✓          |                    |       |
| 10.7.  | IdentifierRef      | ✓          |                    |       |
| 10.8.  | LetClause          | ✗          |                    |       |
| 10.9.  | QueryLetRef        | ✗          |                    |       |
| 10.10. | RelationshipClause | ✓          |                    |       |
| 10.11. | ReturnClause       | ✓          |                    |       |
| 10.12. | AggregateClause    | ✗          |                    |       |
| 10.13. | SortClause         | ✓          |                    |       |
| 10.14. | With               | ✓          |                    |       |
| 10.15. | Without            | ✓          |                    |       |

### 11. External Data

| Num   | Group          | Expression | State          | Notes |
|-------|----------------|------------|----------------|-------|
| 11.1. | Retrieve       | !          | no date ranges |       |
| 11.2. | IncludeElement | ✗          |                |       |

### 12. Comparison Operators

| Num   | Group          | Expression | State | Notes |
|-------|----------------|------------|-------|-------|
| 12.1. | Equal          | ✓          |
| 12.2. | Equivalent     | ✓          |
| 12.3. | Greater        | ✓          |
| 12.4. | GreaterOrEqual | ✓          |
| 12.5. | Less           | ✓          |
| 12.6. | LessOrEqual    | ✓          |
| 12.7. | NotEqual       | ✓          |

### 13. Logical Operators

| Num   | Group   | Expression | State | Notes |
|-------|---------|------------|-------|-------|
| 13.1. | And     | ✓          |
| 13.2. | Implies | ✓          |
| 13.3. | Not     | ✓          |
| 13.4. | Or      | ✓          |
| 13.5. | Xor     | ✓          |

### 14. Nullological Operators

| Num   | Group    | Expression | State | Notes |
|-------|----------|------------|-------|-------|
| 14.1. | Null     | ✓          |
| 14.2. | Coalesce | ✓          |
| 14.3. | IsFalse  | ✓          |
| 14.4. | IsNull   | ✓          |
| 14.5. | IsTrue   | ✓          |

### 15. Conditional Operators

| Num   | Group | Expression | State | Notes |
|-------|-------|------------|-------|-------|
| 15.1. | Case  | ✓          |
| 15.2. | If    | ✓          |

### 16. Arithmetic Operators

| Num    | Group           | Expression | State | Notes |
|--------|-----------------|------------|-------|-------|
| 16.1.  | Abs             | ✓          |
| 16.2.  | Add             | ✓          |
| 16.3.  | Ceiling         | ✓          |
| 16.4.  | Divide          | ✓          |
| 16.5.  | Exp             | ✓          |
| 16.6.  | Floor           | ✓          |
| 16.7.  | HighBoundary    | ✗          |
| 16.8.  | Log             | ✓          |
| 16.9.  | LowBoundary     | ✗          |
| 16.10. | Ln              | ✓          |
| 16.11. | MaxValue        | ✓          |
| 16.12. | MinValue        | ✓          |
| 16.13. | Modulo          | ✓          |
| 16.14. | Multiply        | ✓          |
| 16.15. | Negate          | ✓          |
| 16.16. | Power           | ✓          |
| 16.17. | Precision       | ✗          |
| 16.18. | Predecessor     | ✓          |
| 16.19. | Round           | ✓          |
| 16.20. | Subtract        | ✓          |
| 16.21. | Successor       | ✓          |
| 16.22. | Truncate        | ✓          |
| 16.23. | TruncatedDivide | ✓          |

### 17. String Operators

| Num    | Group          | Expression | State | Notes |
|--------|----------------|------------|-------|-------|
| 17.1.  | Combine        | ✓          |
| 17.2.  | Concatenate    | ✓          |
| 17.3.  | EndsWith       | ✓          |
| 17.4.  | Equal          | ✓          |
| 17.5.  | Equivalent     | ✗          |
| 17.6.  | Indexer        | ✓          |
| 17.7.  | LastPositionOf | ✓          |
| 17.8.  | Length         | ✓          |
| 17.9.  | Lower          | ✓          |
| 17.10. | Matches        | ✓          |
| 17.11. | Not Equal      | ✓          |
| 17.12. | PositionOf     | ✓          |
| 17.13. | ReplaceMatches | ✓          |
| 17.14. | Split          | ✓          |
| 17.15. | SplitOnMatches | ✗          |
| 17.16. | StartsWith     | ✓          |
| 17.17. | Substring      | ✓          |
| 17.18. | Upper          | ✓          |

### 18. Date and Time Operators

| Num    | Group                 | Expression | State                   | Notes |
|--------|-----------------------|------------|-------------------------|-------|
| 18.1.  | Add                   | ✓          |                         |       |
| 18.2.  | After                 | ✓          |                         |       |
| 18.3.  | Before                | ✓          |                         |       |
| 18.4.  | Equal                 | ✓          |                         |       |
| 18.5.  | Equivalent            | ✓          |                         |       |
| 18.6.  | Date                  | ✓          |                         |       |
| 18.7.  | DateFrom              | ✓          |                         |       |
| 18.8.  | DateTime              | ✓          |                         |       |
| 18.9.  | DateTimeComponentFrom | ✓          |                         |       |
| 18.10. | DifferenceBetween     | !          | same as DurationBetween |       |
| 18.11. | DurationBetween       | ✓          |                         |       |
| 18.12. | Not Equal             | ✓          |                         |       |
| 18.13. | Now                   | ✓          |                         |       |
| 18.14. | SameAs                | ✓          |                         |       |
| 18.15. | SameOrBefore          | ✓          |                         |       |
| 18.16. | SameOrAfter           | ✓          |                         |       |
| 18.17. | Subtract              | ✓          |                         |       |
| 18.18. | Time                  | ✓          |                         |       |
| 18.19. | TimeFrom              | ✓          |                         |       |
| 18.20. | TimezoneOffsetFrom    | ✓          |                         |       |
| 18.21. | TimeOfDay             | ✓          |                         |       |
| 18.22. | Today                 | ✓          |                         |       |

### 19. Interval Operators

| Num    | Group            | Expression | State | Notes |
|--------|------------------|------------|-------|-------|
| 19.1.  | Interval         | ✓          |
| 19.2.  | After            | ✓          |
| 19.3.  | Before           | ✓          |
| 19.4.  | Collapse         | ✓          |
| 19.5.  | Contains         | ✓          |
| 19.6.  | End              | ✓          |
| 19.7.  | Ends             | ✓          |
| 19.8.  | Equal            | ✓          |
| 19.9.  | Equivalent       | ✓          |
| 19.10. | Except           | ✓          |
| 19.11. | Expand           | ✗          |
| 19.12. | In               | ✓          |
| 19.13. | Includes         | ✓          |
| 19.14. | IncludedIn       | ✓          |
| 19.15. | Intersect        | ✓          |
| 19.16. | Meets            | ✓          |
| 19.17. | MeetsBefore      | ✓          |
| 19.18. | MeetsAfter       | ✓          |
| 19.19. | Not Equal        | ✓          |
| 19.20. | Overlaps         | ✓          |
| 19.21. | OverlapsBefore   | ✓          |
| 19.22. | OverlapsAfter    | ✓          |
| 19.23. | PointFrom        | ✓          |
| 19.24. | ProperContains   | ✓          |
| 19.25. | ProperIn         | ✓          |
| 19.26. | ProperIncludes   | ✓          |
| 19.27. | ProperIncludedIn | ✓          |
| 19.28. | Size             | ✗          |
| 19.29. | Start            | ✓          |
| 19.30. | Starts           | ✓          |
| 19.31. | Union            | ✓          |
| 19.32. | Width            | ✓          |

### 20. List Operators

| Num    | Group            | Expression | State      | Notes |
|--------|------------------|------------|------------|-------|
| 20.1.  | List             | ✓          |            |       |
| 20.2.  | Contains         | ✓          |            |       |
| 20.3.  | Current          | ✓          |            |       |
| 20.4.  | Distinct         | ✓          |            |       |
| 20.5.  | Equal            | ✓          |            |       |
| 20.6.  | Equivalent       | ✓          |            |       |
| 20.7.  | Except           | ✓          |            |       |
| 20.8.  | Exists           | ✓          |            |       |
| 20.9.  | Filter           | ✓          |            |       |
| 20.10. | First            | ✓          |            |       |
| 20.11. | Flatten          | ✓          |            |       |
| 20.12. | ForEach          | ✓          |            |       |
| 20.13. | In               | ✓          |            |       |
| 20.14. | Includes         | ✓          |            |       |
| 20.15. | IncludedIn       | ✓          |            |       |
| 20.16. | IndexOf          | ✓          |            |       |
| 20.17. | Intersect        | ✓          |            |       |
| 20.18. | Last             | !          | no orderBy |       |
| 20.19. | Not Equal        | ✓          |            |       |
| 20.20. | ProperContains   | ✓          |            |       |
| 20.21. | ProperIn         | ✓          |            |       |
| 20.22. | ProperIncludes   | ✓          |            |       |
| 20.23. | ProperIncludedIn | ✓          |            |       |
| 20.24. | Repeat           | ✗          |            |       |
| 20.25. | SingletonFrom    | ✓          |            |       |
| 20.26. | Slice            | ✓          |            |       |
| 20.27. | Sort             | ✓          |            |       |
| 20.28. | Times            | ✓          |            |       |
| 20.29. | Union            | ✓          |            |       |

### 21. Aggregate Operators

| Num    | Group              | Expression | State   | Notes                  |
|--------|--------------------|------------|---------|------------------------|
| 21.1.  | AllTrue            | !          | no path | path not used from CQL |
| 21.2.  | AnyTrue            | !          | no path | path not used from CQL |
| 21.3.  | Avg                | !          | no path | path not used from CQL |
| 21.4.  | Count              | !          | no path | path not used from CQL |
| 21.5.  | GeometricMean      | !          | no path | path not used from CQL |
| 21.6.  | Product            | !          | no path | path not used from CQL |
| 21.7.  | Max                | !          | no path | path not used from CQL |
| 21.8.  | Median             | !          | no path | path not used from CQL |
| 21.9.  | Min                | !          | no path | path not used from CQL |
| 21.10. | Mode               | !          | no path | path not used from CQL |
| 21.11. | PopulationVariance | !          | no path | path not used from CQL |
| 21.12. | PopulationStdDev   | !          | no path | path not used from CQL |
| 21.13. | Sum                | !          | no path | path not used from CQL |
| 21.14. | StdDev             | !          | no path | path not used from CQL |
| 21.15. | Variance           | !          | no path | path not used from CQL |

### 22. Type Operators

| Num    | Group              | Expression | State         | Notes |
|--------|--------------------|------------|---------------|-------|
| 22.1.  | As                 | !          | no strictness |       |
| 22.2.  | CanConvert         | ✗          |               |       |
| 22.3.  | CanConvertQuantity | ✓          |               |       |
| 22.4.  | Children           | ✓          |               |       |
| 22.5.  | Convert            | ✗          |               |       |
| 22.6.  | ConvertQuantity    | ✓          |               |       |
| 22.7.  | ConvertsToBoolean  | ✓          |               |       |
| 22.8.  | ConvertsToDate     | ✓          |               |       |
| 22.9.  | ConvertsToDateTime | ✓          |               |       |
| 22.10. | ConvertsToDecimal  | ✓          |               |       |
| 22.11. | ConvertsToLong     | ✓          |               |       |
| 22.12. | ConvertsToInteger  | ✓          |               |       |
| 22.13. | ConvertsToQuantity | ✓          |               |       |
| 22.14. | ConvertsToRatio    | ✓          |               |       |
| 22.15. | ConvertsToString   | ✓          |               |       |
| 22.16. | ConvertsToTime     | ✓          |               |       |
| 22.17. | Descendents        | ✓          |               |       |
| 22.18. | Is                 | ✓          |               |       |
| 22.19. | ToBoolean          | ✓          |               |       |
| 22.20. | ToChars            | ✓          |               |       |
| 22.21. | ToConcept          | ✓          |               |       |
| 22.22. | ToDate             | ✓          |               |       |
| 22.23. | ToDateTime         | ✓          |               |       |
| 22.24. | ToDecimal          | ✓          |               |       |
| 22.25. | ToInteger          | ✓          |               |       |
| 22.26. | ToList             | ✓          |               |       |
| 22.27. | ToLong             | ✓          |               |       |
| 22.28. | ToQuantity         | ✓          |               |       |
| 22.29. | ToRatio            | ✓          |               |       |
| 22.30. | ToString           | ✓          |               |       |
| 22.31. | ToTime             | ✓          |               |       |

### 23. Clinical Operators

| Num    | Group           | Expression | State | Notes |
|--------|-----------------|------------|-------|-------|
| 23.1.  | AnyInCodeSystem | ✗          |
| 23.2.  | AnyInValueSet   | ✗          |
| 23.3.  | CalculateAge    | ✓          |
| 23.4.  | CalculateAgeAt  | ✓          |
| 23.5.  | Equal           | ✓          |
| 23.6.  | Equivalent      | ✓          |
| 23.7.  | InCodeSystem    | ✗          |
| 23.8.  | InValueSet      | ✓          |
| 23.9.  | ExpandValueSet  | ✗          |
| 23.10. | Not Equal       | ✓          |
| 23.11. | SubsumedBy      | ✗          |
| 23.12. | Subsumes        | ✗          |

### 24. Errors and Messages

| Num           | Group | Expression | State | Notes |
|---------------|-------|------------|-------|-------|
| 24.1. Message | ✗     |
