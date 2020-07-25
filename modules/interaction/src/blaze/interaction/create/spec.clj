(ns blaze.interaction.create.spec
  (:require
    [blaze.executors :as ex]
    [clojure.spec.alpha :as s]))


(s/def :blaze.interaction.create/executor
  ex/executor?)
