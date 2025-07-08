(ns blaze.terminology-service-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.spec]
   [clojure.spec.alpha :as s]))

(s/fdef ts/code-systems
  :args (s/cat :terminology-service :blaze/terminology-service)
  :ret ac/completable-future?)

(s/fdef ts/code-system-validate-code
  :args (s/cat :terminology-service :blaze/terminology-service
               :params :fhir/Parameters)
  :ret ac/completable-future?)

(s/fdef ts/code-system-lookup
        :args (s/cat :terminology-service :blaze/terminology-service
                     :params :fhir/Parameters)
        :ret ac/completable-future?)

(s/fdef ts/expand-value-set
  :args (s/cat :terminology-service :blaze/terminology-service
               :params :fhir/Parameters)
  :ret ac/completable-future?)

(s/fdef ts/value-set-validate-code
  :args (s/cat :terminology-service :blaze/terminology-service
               :params :fhir/Parameters)
  :ret ac/completable-future?)
