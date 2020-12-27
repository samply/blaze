# Library FHIR Client

The FHIR Client library provides functions for the communication with FHIR servers.

## Usage

### Read a Resource

```clojure
(require '[blaze.fhir-client :as fhir-client])

@(fhir-client/read "https://blaze.life.uni-leipzig.de/fhir" "Patient" "0")
;;=> {:fhir/type :fhir/Patient :id "0"}
```
