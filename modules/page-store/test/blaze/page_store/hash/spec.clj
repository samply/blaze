(ns blaze.page-store.hash.spec
  (:require
   [blaze.page-store.hash :as-alias hash]
   [blaze.spec]
   [clojure.spec.alpha :as s])
  (:import
   [com.google.common.hash HashCode]))

(set! *warn-on-reflection* true)

(s/def :blaze.page-store/hash-code
  (s/and #(instance? HashCode %) #(= 256 (.bits ^HashCode %))))

(s/def ::hash/hash
  :blaze.page-store/hash-code)

(s/def ::hash/hashes
  (s/coll-of (s/coll-of :blaze.page-store/hash-code :kind vector?)
             :kind vector?))

(s/def ::hash/clauses
  (s/map-of :blaze.page-store/hash-code :blaze.db.query/clause))
