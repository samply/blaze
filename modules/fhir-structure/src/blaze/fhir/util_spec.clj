(ns blaze.fhir.util-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.fhir.util :as fu]
   [clojure.spec.alpha :as s]))

(s/fdef fu/subsetted?
  :args (s/cat :coding map?)
  :ret boolean?)

(s/fdef fu/version-cmp
  :args (s/cat :v1 (s/nilable string?) :v2 (s/nilable string?))
  :ret int?)

(s/fdef fu/sort-by-priority
  :args (s/cat :resources (s/coll-of :fhir/Resource))
  :ret (s/coll-of :fhir/Resource))

(s/fdef fu/coerce-params
  :args (s/cat :specs map? :parameters :fhir/Parameters)
  :ret map?)
