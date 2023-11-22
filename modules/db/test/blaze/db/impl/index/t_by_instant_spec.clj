(ns blaze.db.impl.index.t-by-instant-spec
  (:require
   [blaze.db.impl.index.t-by-instant :as t-by-instant]
   [blaze.db.kv.spec]
   [blaze.db.spec]
   [blaze.db.tx-log.spec]
   [clojure.spec.alpha :as s]))

(s/fdef t-by-instant/t-by-instant
  :args (s/cat :snapshot :blaze.db/kv-snapshot :instant :blaze.db.tx/instant)
  :ret (s/nilable :blaze.db/t))

(s/fdef t-by-instant/index-entry
  :args (s/cat :instant :blaze.db.tx/instant :t :blaze.db/t)
  :ret :blaze.db.kv/put-entry)
