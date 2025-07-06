(ns blaze.db.impl.index.multi-version-id.spec
  (:require
   [blaze.db.impl.index :as-alias index]
   [clojure.spec.alpha :as s])
  (:import
   [blaze.db.impl.index MultiVersionId]))

(s/def ::index/multi-version-id
  #(instance? MultiVersionId %))
