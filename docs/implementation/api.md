# FHIR API

## Paging Sessions

A paging session begins with an initial FHIR search request. It ensures that all subsequently requested pages share the same database state and query clauses.

When the initial FHIR search request is made via POST, the query clauses should not be exposed in the page links. For this reason, the page store exists. The page store allows query clauses to be exchanged for a token. That token is then placed in the page links instead of the original query clauses.

In the standalone storage deployment, the page store is implemented in-memory. To save space, each query clause is stored separately to avoid storing combinations of query clauses multiple times. A typical example is queries for observations with a certain code across multiple patients. In this case, the FHIR search URL is:

```text
[base]/Observation?patient=Patient/1,Patient/2&code=http://loinc.org|26464-8
```

The query clauses would be:

```clojure
[["patient" "Patient/1" "Patient/2"] ["code" "http://loinc.org|26464-8"]]
```

Storing the list of query clauses all at once would result in storing the patient clause again for the next LOINC code. Instead, the patient clause and the LOINC clause are stored individually. The token is the SHA256 hash of the string contents of a clause. Therefore, the page store contents are as follows:

```clojure
{"TOKEN_0" ["patient" "Patient/1" "Patient/2"]
 "TOKEN_1" ["code" "http://loinc.org|26464-8"]
 "TOKEN_2" ["TOKEN_0" "TOKEN_1"]}
```

The final token is `TOKEN_2`, which is placed in the page links. A second query with the same patients and another LOINC code will result in the following page store contents:

```clojure
{"TOKEN_0" ["patient" "Patient/1" "Patient/2"]
 "TOKEN_1" ["code" "http://loinc.org|26464-8"]
 "TOKEN_2" ["TOKEN_0" "TOKEN_1"]
 "TOKEN_3" ["code" "http://loinc.org|26453-1"]
 "TOKEN_4" ["TOKEN_0" "TOKEN_3"]}
```

The final token for the second query is `TOKEN_4`. It will share `TOKEN_0` from the unchanged patient clause.
