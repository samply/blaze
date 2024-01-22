(ns blaze.db.impl.index.resource-as-of.spec
  (:require
   [blaze.db.impl.index.resource-as-of :as-alias rao]
   [clojure.spec.alpha :as s]))

(s/def ::rao/resource-handle
  ifn?)
