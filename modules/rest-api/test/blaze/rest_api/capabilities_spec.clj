(ns blaze.rest-api.capabilities-spec
  (:require
    [blaze.rest-api.capabilities :as capabilities]
    [blaze.rest-api.capabilities.spec]
    [clojure.spec.alpha :as s]))


(s/fdef capabilities/capabilities-handler
  :args (s/cat :context ::capabilities/context))
