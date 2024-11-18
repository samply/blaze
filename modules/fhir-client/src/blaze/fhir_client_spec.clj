(ns blaze.fhir-client-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.async.comp-spec]
   [blaze.async.flow :as flow]
   [blaze.async.flow-spec]
   [blaze.fhir-client :as fhir-client]
   [blaze.fhir-client.spec]
   [blaze.fhir.spec]
   [blaze.fhir.writing-context.spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s])
  (:import
   [java.nio.file Path]))

(s/fdef fhir-client/metadata
  :args (s/cat :base-uri string? :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/read
  :args (s/cat :base-uri string? :type :fhir.resource/type :id :blaze.resource/id
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/create
  :args (s/cat :base-uri string? :resource :fhir/Resource
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/update
  :args (s/cat :base-uri string? :resource :fhir/Resource
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/delete
  :args (s/cat :base-uri string? :type :fhir.resource/type :id :blaze.resource/id
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/delete-history
  :args (s/cat :base-uri string? :type :fhir.resource/type :id :blaze.resource/id
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/transact
  :args (s/cat :base-uri string? :bundle :fhir/Resource
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/execute-type-get
  :args (s/cat :base-uri string? :type :fhir.resource/type :name string?
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/execute-type-post
  :args (s/cat :base-uri string? :type :fhir.resource/type :name string?
               :params :fhir/Parameters
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/search-type-publisher
  :args (s/cat :base-uri string? :type :fhir.resource/type
               :opts (s/? ::fhir-client/options))
  :ret flow/publisher?)

(s/fdef fhir-client/resource-processor
  :args (s/cat)
  :ret flow/processor?)

(s/fdef fhir-client/search-type
  :args (s/cat :base-uri string? :type :fhir.resource/type
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/search-system-publisher
  :args (s/cat :base-uri string? :opts (s/? ::fhir-client/options))
  :ret flow/publisher?)

(s/fdef fhir-client/search-system
  :args (s/cat :base-uri string? :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/history-instance-publisher
  :args (s/cat :base-uri string? :type :fhir.resource/type :id :blaze.resource/id
               :opts (s/? ::fhir-client/options))
  :ret flow/publisher?)

(s/fdef fhir-client/history-instance
  :args (s/cat :base-uri string? :type :fhir.resource/type :id :blaze.resource/id
               :opts (s/? ::fhir-client/options))
  :ret ac/completable-future?)

(s/fdef fhir-client/spit
  :args (s/cat :writing-context :blaze.fhir/writing-context
               :dir #(instance? Path %) :publisher flow/publisher?)
  :ret ac/completable-future?)
