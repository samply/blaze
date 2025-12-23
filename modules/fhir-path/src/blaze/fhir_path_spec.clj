(ns blaze.fhir-path-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.fhir-path :as fhir-path]
   [blaze.fhir.spec-spec]
   [blaze.fhir.spec.type.system-spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/def :blaze.fhir-path/expression
  #(satisfies? fhir-path/Expression %))

(s/def :blaze.fhir-path/resolver
  #(satisfies? fhir-path/Resolver %))

(s/fdef fhir-path/eval
  :args (s/cat :resolver :blaze.fhir-path/resolver
               :expr :blaze.fhir-path/expression
               :value #(keyword? (:fhir/type %)))
  :ret (s/or :coll (s/coll-of some?) :anomaly ::anom/anomaly))

(s/fdef fhir-path/compile
  :args (s/cat :expr string?)
  :ret (s/or :expr :blaze.fhir-path/expression :anomaly ::anom/anomaly))
