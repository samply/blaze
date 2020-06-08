(ns blaze.db.impl.iterators-spec
  (:require
    [blaze.db.impl.iterators :as i]
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s]))


(s/fdef i/kvs
  :args (s/cat :iter :blaze.db/kv-iterator :decode fn? :start-key bytes?))
