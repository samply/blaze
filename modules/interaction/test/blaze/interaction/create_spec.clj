(ns blaze.interaction.create-spec
  (:require
    [blaze.db.api-spec]
    [blaze.interaction.create :as create]
    [clojure.spec.alpha :as s]))


(s/fdef create/handler
  :args (s/cat :node :blaze.db/node)
  :ret fn?)
