(ns blaze.db.impl.index.resource-search-param-value.spec
  (:require
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [clojure.spec.alpha :as s])
  (:import
    [java.lang AutoCloseable]))


(s/def ::r-sp-v/next-value
  (s/and ifn? #(instance? AutoCloseable %)))


(s/def ::r-sp-v/next-value-prev
  (s/and ifn? #(instance? AutoCloseable %)))
