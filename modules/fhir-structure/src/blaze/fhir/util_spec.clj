(ns blaze.fhir.util-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.fhir.util :as fu]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef fu/subsetted?
  :args (s/cat :coding map?)
  :ret boolean?)

(s/fdef fu/version-cmp
  :args (s/cat :v1 (s/nilable string?) :v2 (s/nilable string?))
  :ret int?)

(s/fdef fu/sort-by-priority
  :args (s/cat :resources (s/coll-of :fhir/Resource))
  :ret (s/coll-of :fhir/Resource))

(s/fdef fu/split-canonical
  :args (s/cat :canonical string?)
  :ret (s/coll-of string? :kind vector? :min-count 1 :max-count 2))

(s/fdef fu/coerce-params
  :args (s/cat :specs map? :parameters :fhir/Parameters)
  :ret (s/or :params (s/map-of simple-keyword? any?) :anomaly ::anom/anomaly))

(s/fdef fu/coerce-integer
  :args (s/cat :x any?)
  :ret (s/or :value int? :anomaly ::anom/anomaly))

(s/fdef fu/coerce-boolean
  :args (s/cat :x any?)
  :ret (s/or :value boolean? :anomaly ::anom/anomaly))

(s/fdef fu/coerce-string
  :args (s/cat :x any?)
  :ret (s/or :value string? :anomaly ::anom/anomaly))

(s/fdef fu/coerce-uri
  :args (s/cat :x any?)
  :ret (s/or :value string? :anomaly ::anom/anomaly))
