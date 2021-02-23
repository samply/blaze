(ns blaze.db.impl.search-param.quantity.spec
  (:require
    [blaze.byte-string :refer [byte-string?]]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.impl.search-param.quantity.value/op
  #{:eq :gt :lt :ge :le})


(s/def :blaze.db.impl.search-param.quantity.value/lower-bound
  byte-string?)


(s/def :blaze.db.impl.search-param.quantity.value/upper-bound
  byte-string?)


(s/def :blaze.db.impl.search-param.quantity.value/exact-value
  byte-string?)


(s/def :blaze.db.impl.search-param.quantity/value
  (s/keys
    :req-un
    [:blaze.db.impl.search-param.quantity.value/op
     (or :blaze.db.impl.search-param.quantity.value/lower-bound
         :blaze.db.impl.search-param.quantity.value/upper-bound
         :blaze.db.impl.search-param.quantity.value/exact-value)]))
