(ns blaze.interaction.search-type-spec
  (:require
    [blaze.interaction.search-type :as search-type]
    [clojure.spec.alpha :as s]))


(s/fdef search-type/handler
  :args (s/cat :node :blaze.db/node)
  :ret fn?)
