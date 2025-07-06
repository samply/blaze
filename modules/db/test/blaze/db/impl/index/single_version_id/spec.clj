(ns blaze.db.impl.index.single-version-id.spec
  (:require
   [blaze.db.impl.index :as-alias index]
   [clojure.spec.alpha :as s])
  (:import
   [blaze.db.impl.index SingleVersionId]))

(s/def ::index/single-version-id
  #(instance? SingleVersionId %))
