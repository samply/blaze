(ns blaze.rest-api.middleware.sync-spec
  (:require
   [blaze.rest-api.middleware.sync :as sync]
   [clojure.spec.alpha :as s]))

(s/fdef sync/wrap-sync
  :args (s/cat :handler fn?)
  :ret fn?)
