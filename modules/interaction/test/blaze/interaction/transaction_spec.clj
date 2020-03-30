(ns blaze.interaction.transaction-spec
  (:require
    [blaze.executors :refer [executor?]]
    [blaze.interaction.transaction :as transaction]
    [clojure.spec.alpha :as s]))


(s/fdef transaction/handler
  :args (s/cat :node :blaze.db/node :executor executor?)
  :ret fn?)
