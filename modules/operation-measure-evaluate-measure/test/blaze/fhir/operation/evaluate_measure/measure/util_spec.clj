(ns blaze.fhir.operation.evaluate-measure.measure.util-spec
  (:require
    [blaze.fhir.operation.evaluate-measure.measure :as-alias measure]
    [blaze.fhir.operation.evaluate-measure.measure.spec]
    [blaze.fhir.operation.evaluate-measure.measure.util :as u]
    [clojure.spec.alpha :as s]))


(s/fdef u/population
  :args (s/cat :context map? :fhir-type :fhir/type :code any?
               :handles ::measure/handles))
