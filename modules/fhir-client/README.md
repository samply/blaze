# Library FHIR Client

The FHIR Client library provides functions for the communication with FHIR servers.

## Usage

### Read a Resource

```clojure
(require '[blaze.fhir-client :as fhir-client])

@(fhir-client/read "https://r4.blaze-server.org/fhir" "Patient" "0")
;;=> {:fhir/type :fhir/Patient :id "0"}
```
