(ns blaze.rest-api.middleware.ensure-form-body-spec
  (:require
   [blaze.rest-api.middleware.ensure-form-body :as ensure-form-body]
   [clojure.spec.alpha :as s]))

(s/fdef ensure-form-body/wrap-ensure-form-body
  :args (s/cat :handler fn?)
  :ret fn?)
