(ns blaze.fhir-path-spec
  (:require
    [blaze.fhir.spec]
    [blaze.fhir-path :as fhir-path]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/def :blaze.fhir-path/expression
  #(satisfies? fhir-path/Expression %))


(s/def :blaze.fhir-path/resolver
  #(satisfies? fhir-path/Resolver %))


(s/fdef fhir-path/eval
  :args (s/cat :resolver :blaze.fhir-path/resolver
               :expr :blaze.fhir-path/expression
               :resource :blaze/resource)
  :ret (s/or :coll (s/coll-of some?) :anomaly ::anom/anomaly))


(s/fdef fhir-path/compile
  :args (s/cat :expr string?)
  :ret :blaze.fhir-path/expression)
