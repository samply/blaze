(ns blaze.middleware.params-spec
  (:require
   [blaze.middleware.params :as params]
   [clojure.spec.alpha :as s]))

(s/fdef params/wrap-params
  :args (s/cat :handler fn?)
  :ret fn?)

(s/fdef params/wrap-invalid-params
  :args (s/cat :handler fn?)
  :ret fn?)
