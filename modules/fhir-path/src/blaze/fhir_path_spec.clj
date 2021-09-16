(ns blaze.fhir-path-spec
  (:require
    [blaze.anomaly-spec]
    [blaze.fhir-path :as fhir-path]
    [blaze.fhir-path.protocols :as p]
    [blaze.fhir.spec-spec]
    [blaze.fhir.spec.type.system-spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


;; expressions are transducers
(s/def :blaze.fhir-path/expression
  ifn?)


(s/def :blaze.fhir-path/resolver
  #(satisfies? p/Resolver %))


(s/fdef fhir-path/eval
  :args (s/cat :expr :blaze.fhir-path/expression :coll (s/coll-of some?))
  :ret (s/or :coll (s/coll-of some?) :anomaly ::anom/anomaly))


(s/fdef fhir-path/compile
  :args (s/cat :resolver :blaze.fhir-path/resolver :expr string?)
  :ret (s/or :expr :blaze.fhir-path/expression :anomaly ::anom/anomaly))
