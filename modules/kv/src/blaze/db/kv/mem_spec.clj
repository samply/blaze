(ns blaze.db.kv.mem-spec
  (:require
    [blaze.db.kv :as kv]
    [blaze.db.kv-spec]
    [blaze.db.kv.mem :refer [new-mem-kv-store]]
    [clojure.spec.alpha :as s]))


(s/fdef new-mem-kv-store
  :args (s/cat :column-families (s/? map?))
  :ret kv/store?)
