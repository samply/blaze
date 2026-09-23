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

## Existing Expansions <Badge type="warning" text="Since 1.12.0"/>

A ValueSet may already contain an expansion, for example a stored expansion that ships with a terminology package. Before using it, Blaze checks whether the In Parameters are consistent with the parameters recorded in `ValueSet.expansion.parameter`.

* If the parameters are consistent, the existing expansion is used. The following In Parameters are applied on top of it with the same result as expanding the compose:
  * `count` — the existing concepts are truncated
  * `activeOnly` — inactive concepts are removed if the existing concepts carry `inactive` flags
  * `includeDesignations` — designations are removed unless `includeDesignations` is true
  * `includeDefinition` — the compose is removed unless `includeDefinition` is true
  * `excludeNested` — nested concepts are flattened
* If the parameters are inconsistent and the ValueSet has a compose, the compose is expanded.
* If the parameters are inconsistent and the ValueSet has no compose, an error with status `409 Conflict` is returned.

The parameters are inconsistent if one of the following applies:

* the requested `filter` differs from the one recorded in the existing expansion, including a `filter` requested for an existing expansion without a recorded one
* the existing expansion records `activeOnly` true, but it isn't requested, or `activeOnly` is requested, but the existing concepts carry no `inactive` flags
* `includeDesignations` is requested, but not recorded in the existing expansion
* the requested `displayLanguage` differs from the recorded one
* the requested `property` codes differ from the recorded ones
* a `system-version` is requested for a code system with a different or unknown version in the existing expansion
* the existing expansion is incomplete (it records a `count`, has an `offset` greater than 0 or a `total` greater than the number of concepts) and isn't requested with the same `count` and no additional `activeOnly`

A `filter` isn't applied on top of an existing expansion, because the concepts of an existing expansion usually lack the designations the [Filter Parameter](#filter-parameter) searches in the code system. So a ValueSet with an existing expansion and a compose is filtered by expanding the compose.

A new `ValueSet.expansion.identifier` is only generated if the existing expansion was changed.

## Resolution of ValueSet and CodeSystem Resources

More on resolution of terminology resources can be found [here](../../terminology-service/resource-resolution.md).

The official documentation can be found [here][1].

[1]: <http://hl7.org/fhir/R4/valueset-operation-expand.html>
