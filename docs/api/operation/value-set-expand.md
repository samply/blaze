# Operation \$expand on ValueSet <Badge type="info" text="Feature: TERMINOLOGY_SERVICE"/> <Badge type="warning" text="Since 0.32"/>

The \$expand operation can be used to expand all codes of a ValueSet.

```
GET [base]/ValueSet/$expand
GET [base]/ValueSet/[id]/$expand
```

## In Parameters

| Name              | Cardinality | Type      | Documentation                                                                                                                                                                                 |
|-------------------|-------------|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| url               | 0..1        | uri       | A canonical reference to a value set. [VCL](../../terminology-service/vcl.md) can be used. Otherwise the value set has to be already stored on the server.                                    |
| valueSet          | 0..1        | ValueSet  | The value set is provided directly as part of the request.                                                                                                                                    |
| valueSetVersion   | 0..1        | string    | The business version of the value set. If not given and multiple versions exist, an arbitrary version will be chosen.                                                                         | 
| filter            | 0..1        | string    | A text filter to restrict the expansion to concepts matching the filter. See [Filter Parameter](#filter-parameter) below.                                                                     |
| offset            | 0..1        | integer   | Paging support - where to start if a subset is desired. Currently only 0 is supported.                                                                                                        | 
| count             | 0..1        | integer   | Paging support - how many codes should be provided in a partial page view. If count = 0, the client is asking how large the expansion is.                                                     | 
| includeDefinition | 0..1        | boolean   | Controls whether the value set definition is included or excluded in value set expansions. Defaults to false.                                                                                 | 
| activeOnly        | 0..1        | boolean   | Controls whether inactive concepts are included or excluded in value set expansions. Defaults to true.                                                                                        | 
| excludeNested     | 0..1        | boolean   | Controls whether or not the value set expansion may nest codes or not (i.e. ValueSet.expansion.contains.contains).                                                                            | 
| displayLanguage   | 0..1        | code      | Specifies the language to be used for description in the expansions i.e. the language to be used for ValueSet.expansion.contains.display.                                                     | 
| property          | 0..*        | code      | A request to return a particular property in the expansion.                                                                                                                                   | 
| system-version    | 0..*        | canonical | Specifies a version to use for a system, if the value set does not specify which one to use. The format is the same as a canonical URL: \[system\]\|\[version\] - e.g. http://loinc.org\|2.56 | 
| tx-resource       | 0..*        | code      | Used by the Java validator.                                                                                                                                                                   |

## Filter Parameter

The `filter` parameter enables typeahead/autocomplete search over value set concepts. It performs a full-text search against concept display names and designations, returning results ranked by relevance.

### Matching Behavior

The filter text is split into individual words. Each word is matched against the indexed text using two strategies:

* **Prefix matching** — a word matches if any indexed term starts with it. For example, `blood pres` matches "Systolic **blood** **pres**sure" because "blood" is a prefix of "blood" and "pres" is a prefix of "pressure".
* **Fuzzy matching** — a word matches if any indexed term is within an edit distance of 2 (insertions, deletions, or substitutions). For example, `diabtes` matches "**diabetes**" despite the transposed letters.

All words in the filter must match (AND logic), but each word can match via either strategy.

### Examples

| Filter         | Matches                                              | Reason                           |
|----------------|------------------------------------------------------|----------------------------------|
| `blood pres`   | Systolic blood pressure                              | Prefix match on both words       |
| `diabtes`      | Diabetes mellitus                                    | Fuzzy match (transposed letters) |
| `hypertensoin` | Essential hypertension                               | Fuzzy match (transposed letters) |
| `sugar`        | Diabetes mellitus (with designation "Sugar disease") | Match on designation value       |

### Scope

The search covers:

* Concept display names
* Concept designations (all languages)

Results are ranked by relevance, with closer matches scored higher.

## Resolution of ValueSet and CodeSystem Resources

More on resolution of terminology resources can be found [here](../../terminology-service/resource-resolution.md).

The official documentation can be found [here][1].

[1]: <http://hl7.org/fhir/R4/valueset-operation-expand.html>
