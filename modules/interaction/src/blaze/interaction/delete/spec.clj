(ns blaze.interaction.delete.spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/def :blaze.interaction.delete/executor
  ex/executor?)
