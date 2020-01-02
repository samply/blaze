(ns blaze.fhir.operation.evaluate-measure.handler.impl-spec
  (:require
    [blaze.db.api-spec]
    [blaze.executors :refer [executor?]]
    [blaze.fhir.operation.evaluate-measure.handler.impl :as impl]
    [clojure.spec.alpha :as s])
  (:import
    [java.time Clock]))


(s/fdef impl/handler
  :args (s/cat :clock #(instance? Clock %) :node :blaze.db/node :executor executor?))
