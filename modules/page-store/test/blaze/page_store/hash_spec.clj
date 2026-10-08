(ns blaze.page-store.hash-spec
  (:require
   [blaze.page-store.hash :as hash]
   [blaze.page-store.hash.spec]
   [blaze.page-store.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef hash/hash-clause
  :args (s/cat :clause :blaze.db.query/clause)
  :ret :blaze.page-store/hash-code)

(s/fdef hash/hash-hashes
  :args (s/cat :hashes (s/coll-of (s/coll-of :blaze.page-store/hash-code)))
  :ret :blaze.page-store/hash-code)

(s/fdef hash/encode
  :args (s/cat :hash :blaze.page-store/hash-code)
  :ret :blaze.page-store/token)

(s/fdef hash/decode
  :args (s/cat :token :blaze.page-store/token)
  :ret :blaze.page-store/hash-code)

(s/fdef hash/hash-clauses
  :args (s/cat :clauses :blaze.db.query/clauses)
  :ret (s/keys :req-un [::hash/hash ::hash/hashes ::hash/clauses]))
