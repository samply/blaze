(ns blaze.db.node.spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.node/indexer-executor
  ex/executor?)
