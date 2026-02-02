(ns blaze.page-store.local.hash-spec
  (:require
   [blaze.page-store.local :as-alias local]
   [blaze.page-store.local.hash :as hash]
   [blaze.page-store.local.hash.spec]
   [blaze.page-store.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef hash/hash-clause
  :args (s/cat :clause :blaze.db.query/clause)
  :ret ::local/hash-code)

(s/fdef hash/hash-hashes
  :args (s/cat :hashes (s/coll-of (s/coll-of ::local/hash-code)))
  :ret ::local/hash-code)

(s/fdef hash/encode
  :args (s/cat :hash ::local/hash-code)
  :ret :blaze.page-store/token)
