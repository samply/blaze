(ns blaze.rest-api.middleware.log-spec
  (:require
   [blaze.rest-api.middleware.log :as log]
   [clojure.spec.alpha :as s]))

(s/fdef log/wrap-log
  :args (s/cat :handler fn?)
  :ret fn?)
