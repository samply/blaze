(ns blaze.db.impl.index.index-handle.spec
  (:require
   [blaze.db.impl.index :as-alias index]
   [clojure.spec.alpha :as s])
  (:import
   [blaze.db.impl.index IndexHandle]))

(s/def ::index/handle
  #(instance? IndexHandle %))
