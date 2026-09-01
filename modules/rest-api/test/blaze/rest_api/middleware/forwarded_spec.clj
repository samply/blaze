(ns blaze.rest-api.middleware.forwarded-spec
  (:require
   [blaze.rest-api.middleware.forwarded :as forwarded]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef forwarded/wrap-forwarded
  :args (s/cat :handler fn? :default-base-url :blaze/base-url)
  :ret fn?)
