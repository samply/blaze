(ns blaze.middleware.fhir.validate-spec
  (:require
   [blaze.middleware.fhir.validate :as validate]
   [blaze.validator.spec]
   [clojure.spec.alpha :as s]))

(s/fdef validate/wrap-validate
  :args (s/cat :handler ifn? :validator :blaze/validator))
