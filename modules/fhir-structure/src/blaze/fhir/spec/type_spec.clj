(ns blaze.fhir.spec.type-spec
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [blaze.fhir.spec.type.system-spec]
    [clojure.alpha.spec :as s2]
    [clojure.spec.alpha :as s]))


(s/fdef type/value
  :args (s/cat :x any?)
  :ret (s/nilable system/value?))


(s/fdef type/->Date
  :args (s/cat :value string?)
  :ret (s/or :date type/date? :invalid s2/invalid?))
