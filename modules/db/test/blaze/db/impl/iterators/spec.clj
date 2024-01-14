(ns blaze.db.impl.iterators.spec
  (:require
   [blaze.byte-string-spec]
   [blaze.coll.core-spec]
   [blaze.db.impl.iterators :as i]
   [blaze.db.kv-spec]
   [clojure.spec.alpha :as s]))

(s/def ::i/entry
  #(satisfies? i/Entry %))
