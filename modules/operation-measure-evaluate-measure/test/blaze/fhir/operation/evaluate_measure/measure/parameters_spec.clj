(ns blaze.fhir.operation.evaluate-measure.measure.parameters-spec
  (:require
   [blaze.fhir.operation.evaluate-measure.measure.parameters :as parameters]
   [blaze.fhir.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef parameters/effective-parameters
  :args (s/cat :parameter-default-values map?
               :parameters (s/nilable :fhir/Parameters))
  :ret (s/or :parameters map? :anomaly ::anom/anomaly))
