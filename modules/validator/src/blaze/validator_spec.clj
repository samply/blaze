(ns blaze.validator-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec]
   [blaze.validator :as validator]
   [blaze.validator.spec]
   [clojure.spec.alpha :as s]))

(s/fdef validator/validate
  :args (s/cat :validator :blaze/validator :resource :fhir/Resource)
  :ret ac/completable-future?)
