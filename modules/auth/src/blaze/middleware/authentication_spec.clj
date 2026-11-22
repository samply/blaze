(ns blaze.middleware.authentication-spec
  (:require
   [blaze.auth.spec]
   [blaze.middleware.authentication :as authentication]
   [clojure.spec.alpha :as s]))

(s/fdef authentication/wrap-authentication
  :args (s/cat :handler fn? :backend :blaze.auth/backend)
  :ret fn?)
