(ns blaze.fhir.operation.evaluate-measure-spec
  (:require
    [blaze.executors :refer [executor?]]
    [blaze.fhir.operation.evaluate-measure :as evaluate-measure]
    [clojure.spec.alpha :as s])
  (:import
    [java.time Clock]))


(s/fdef evaluate-measure/handler
  :args (s/cat :clock #(instance? Clock %) :node :blaze.db/node :executor executor?)
  :ret fn?)
