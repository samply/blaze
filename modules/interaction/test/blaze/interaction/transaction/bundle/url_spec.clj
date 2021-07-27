(ns blaze.interaction.transaction.bundle.url-spec
  (:require
    [blaze.fhir.spec.spec]
    [blaze.interaction.transaction.bundle.url :as url]
    [clojure.spec.alpha :as s]))


(s/fdef url/match-url
  :args (s/cat :url string?)
  :ret (s/or :type-level (s/tuple :fhir.resource/type)
             :instance-level (s/tuple :fhir.resource/type :blaze.resource/id)
             :other nil?))
