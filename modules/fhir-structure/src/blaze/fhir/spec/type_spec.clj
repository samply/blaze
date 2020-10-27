(ns blaze.fhir.spec.type-spec
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.alpha :as s]))


(s/fdef type/value
  :args (s/cat :x any?)
  :ret (s/nilable system/value?))
