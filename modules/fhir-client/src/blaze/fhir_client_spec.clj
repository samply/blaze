(ns blaze.fhir-client-spec
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.async.flow :as flow]
    [blaze.async.flow-spec]
    [blaze.fhir-client :as fhir-client]
    [blaze.fhir-client.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s])
  (:import
    [java.nio.file Path]))


(s/fdef fhir-client/client
  :args (s/cat :http-client (s/? :java.net.http/http-client) :base-uri string?)
  :ret :blaze.fhir-client/client)


(s/fdef fhir-client/metadata
  :args (s/cat :client :blaze.fhir-client/client)
  :ret ac/completable-future?)


(s/fdef fhir-client/read
  :args (s/cat :client :blaze.fhir-client/client :type string? :id string?)
  :ret ac/completable-future?)


(s/fdef fhir-client/update
  :args (s/cat :client :blaze.fhir-client/client :resource :blaze/resource)
  :ret ac/completable-future?)


(s/fdef fhir-client/search-type-publisher
  :args (s/cat :client :blaze.fhir-client/client :type string? :params map?)
  :ret flow/publisher?)


(s/fdef fhir-client/resource-processor
  :args (s/cat)
  :ret flow/processor?)


(s/fdef fhir-client/search-type
  :args (s/cat :client :blaze.fhir-client/client :type string? :params (s/? map?))
  :ret ac/completable-future?)


(s/fdef fhir-client/spit
  :args (s/cat :dir #(instance? Path %) :publisher flow/publisher?)
  :ret ac/completable-future?)
