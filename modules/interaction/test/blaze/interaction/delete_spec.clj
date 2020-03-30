(ns blaze.interaction.delete-spec
  (:require
    [blaze.interaction.delete :as delete]
    [clojure.spec.alpha :as s]))


(s/fdef delete/handler
  :args (s/cat :node :blaze.db/node)
  :ret fn?)
