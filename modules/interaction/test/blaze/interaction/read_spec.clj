(ns blaze.interaction.read-spec
  (:require
    [blaze.interaction.read :as read]
    [clojure.spec.alpha :as s]))


(s/fdef read/handler
  :args (s/cat :node :blaze.db/node)
  :ret fn?)
