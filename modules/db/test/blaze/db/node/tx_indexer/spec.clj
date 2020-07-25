(ns blaze.db.node.tx-indexer.spec
  (:require
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.node/tx-indexer
  (s/keys :req-un [:blaze.db/kv-store]))
