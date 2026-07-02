(ns blaze.admin-api.validator-spec
  (:require
   [blaze.admin-api.validator :as validator]
   [blaze.admin-api.validator.spec]
   [clojure.spec.alpha :as s]))

(s/fdef validator/validate
  :args (s/cat :validator :blaze.admin-api/validator :source string?)
  :ret map?)
