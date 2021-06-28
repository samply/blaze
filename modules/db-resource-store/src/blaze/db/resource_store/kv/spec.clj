(ns blaze.db.resource-store.kv.spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/def :blaze.db.resource-store.kv/executor
  ex/executor?)
