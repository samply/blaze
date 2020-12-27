(ns blaze.fhir-client-spec
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.async.flow :as flow]
    [blaze.async.flow-spec]
    [blaze.fhir-client :as fhir-client]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s])
  (:import
    [java.nio.file Path]))


(s/fdef fhir-client/metadata
  :args (s/cat :base-uri string? :opts (s/? map?))
  :ret ac/completable-future?)


(s/fdef fhir-client/read
  :args (s/cat :base-uri string? :type :fhir.type/name :id :blaze.resource/id
               :opts (s/? map?))
  :ret ac/completable-future?)


(s/fdef fhir-client/update
  :args (s/cat :base-uri string? :resource :blaze/resource :opts (s/? map?))
  :ret ac/completable-future?)


(s/fdef fhir-client/execute-type-get
  :args (s/cat :base-uri string? :type :fhir.type/name :name string?
               :opts (s/? map?))
  :ret ac/completable-future?)


(s/fdef fhir-client/search-type-publisher
  :args (s/cat :base-uri string? :type :fhir.type/name :opts (s/? map?))
  :ret flow/publisher?)


(s/fdef fhir-client/resource-processor
  :args (s/cat)
  :ret flow/processor?)


(s/fdef fhir-client/search-type
  :args (s/cat :base-uri string? :type :fhir.type/name :opts (s/? map?))
  :ret ac/completable-future?)


(s/fdef fhir-client/search-system
  :args (s/cat :base-uri string? :opts (s/? map?))
  :ret ac/completable-future?)


(s/fdef fhir-client/spit
  :args (s/cat :dir #(instance? Path %) :publisher flow/publisher?)
  :ret ac/completable-future?)
