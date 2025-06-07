(ns blaze.page-store.local.hash-spec
  (:require
   [blaze.page-store.local.hash :as hash]
   [blaze.page-store.local.hash.spec]
   [blaze.page-store.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef hash/hash-clause
  :args (s/cat :clause :blaze.db.query/clause)
  :ret :blaze.page-store.local/hash-code)

(s/fdef hash/hash-hashes
  :args (s/cat :hashes (s/coll-of :blaze.page-store.local/hash-code))
  :ret :blaze.page-store.local/hash-code)

(s/fdef hash/encode
  :args (s/cat :hash :blaze.page-store.local/hash-code)
  :ret :blaze.page-store/token)
