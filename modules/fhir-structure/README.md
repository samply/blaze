# FHIR Structure

This module provides [specs][1] for the [FHIR Data Model][2] and functions to conform und unform JSON and XML representation to and from the internal representation used by Blaze.

## Examples

### Conforming a JSON Representation to the Internal Representation

```clojure
(use 'blaze.fhir.spec)

(conform-json {:resourceType "Patient" :id "0"})
;;=> {:fhir/type :fhir/Patient :id "0"}
```

[1]: <https://clojure.org/guides/spec>
[2]: <../../docs/implementation/fhir-data-model.md>
