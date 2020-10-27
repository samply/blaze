(ns blaze.fhir.spec.type.system-spec
  (:require
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.alpha :as s]))


(s/fdef system/type
  :args (s/cat :a any?)
  :ret (s/nilable keyword?))


(s/fdef system/equals
  :args (s/cat :a any? :b any?)
  :ret (s/nilable boolean?))
