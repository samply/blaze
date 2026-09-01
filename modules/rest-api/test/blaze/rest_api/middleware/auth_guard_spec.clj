(ns blaze.rest-api.middleware.auth-guard-spec
  (:require
   [blaze.rest-api.middleware.auth-guard :as auth-guard]
   [clojure.spec.alpha :as s]))

(s/fdef auth-guard/wrap-auth-guard
  :args (s/cat :handler fn?)
  :ret fn?)
