(ns blaze.fhir.operation.evaluate-measure.measure.util-spec
  (:require
   [blaze.fhir.operation.evaluate-measure.measure.util :as u]
   [blaze.fhir.spec.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef u/expression-name
  :args (s/cat :population-path-fn fn? :criteria (s/nilable :fhir/Expression))
  :ret (s/or :expression-name string? :anomaly ::anom/anomaly))
