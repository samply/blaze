(ns blaze.db.impl.search-param.quantity.spec
  (:require
    [blaze.byte-string :refer [byte-string?]]
    [blaze.db.impl.search-param.quantity :as-alias spq]
    [blaze.db.impl.search-param.quantity.value :as-alias value]
    [clojure.spec.alpha :as s]))


(s/def ::value/op
  #{:eq :gt :lt :ge :le})


(s/def ::value/lower-bound
  byte-string?)


(s/def ::value/upper-bound
  byte-string?)


(s/def ::value/exact-value
  byte-string?)


(s/def ::spq/value
  (s/keys
    :req-un
    [::value/op
     (or ::value/lower-bound
         ::value/upper-bound
         ::value/exact-value)]))
