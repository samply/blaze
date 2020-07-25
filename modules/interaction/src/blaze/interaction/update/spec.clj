(ns blaze.interaction.update.spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/def :blaze.interaction.update/executor
  ex/executor?)
