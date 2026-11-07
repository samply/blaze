(ns blaze.db.tx-log.util-spec
  (:require
   [blaze.db.tx-log.spec]
   [blaze.db.tx-log.util :as u]
   [clojure.spec.alpha :as s]))

(s/fdef u/valid-tx-data
  :args (s/cat :tx-data map?)
  :ret (s/nilable :blaze.db/tx-data))
