(ns blaze.fhir.spec.impl.util-spec
  (:require
   [blaze.fhir.spec.impl.util :as u]
   [clojure.alpha.spec :as s2]
   [clojure.spec.alpha :as s]))

(s/fdef u/update-extended-primitives
  :args (s/cat :m any?)
  :ret (s/or :m map? :invalid s2/invalid?))
