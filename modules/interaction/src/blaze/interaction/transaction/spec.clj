(ns blaze.interaction.transaction.spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/def :blaze.interaction.transaction/executor
  ex/executor?)
