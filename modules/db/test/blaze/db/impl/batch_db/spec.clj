(ns blaze.db.impl.batch-db.spec
  (:require
   [blaze.db.impl.batch-db :as-alias batch-db]
   [blaze.db.kv :as-alias kv]
   [blaze.db.kv.spec]
   [clojure.spec.alpha :as s]))

(s/def ::batch-db/context
  (s/keys :req-un [::kv/snapshot]))
