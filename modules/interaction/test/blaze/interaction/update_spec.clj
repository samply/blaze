(ns blaze.interaction.update-spec
  (:require
    [blaze.interaction.update :as update]
    [clojure.spec.alpha :as s]))


(s/fdef update/handler
  :args (s/cat :node :blaze.db/node)
  :ret fn?)
