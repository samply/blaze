(ns blaze.db.impl.query.system-spec
  (:require
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.query.system :as qs]
   [blaze.db.index.query :as-alias query]
   [clojure.spec.alpha :as s]))

(s/fdef qs/->SystemQuery
  :args (s/cat :tids (s/coll-of :blaze.db/tid :kind vector?)
               :clauses ::query/clauses))

(s/fdef qs/system-query
  :args (s/cat :clauses ::query/clauses))

(s/fdef qs/->EmptySystemQuery
  :args (s/cat))
