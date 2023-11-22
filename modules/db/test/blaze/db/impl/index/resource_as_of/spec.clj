(ns blaze.db.impl.index.resource-as-of.spec
  (:require
   [blaze.db.impl.index.resource-as-of :as-alias rao]
   [clojure.spec.alpha :as s])
  (:import
   [java.lang AutoCloseable]))

(s/def ::rao/resource-handle
  (s/and ifn? #(instance? AutoCloseable %)))
