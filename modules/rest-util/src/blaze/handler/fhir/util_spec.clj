(ns blaze.handler.fhir.util-spec
  (:require
    [blaze.fhir.spec]
    [blaze.handler.fhir.util :as util]
    [blaze.http.spec]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(s/fdef util/to-seq
  :args (s/cat :x any?)
  :ret (s/nilable sequential?))


(s/def :ring.request.query-params/key
  string?)


(s/def :ring.request.query-params/value
  (s/or :string string? :strings (s/coll-of string? :min-count 2)))


(s/def :ring.request/query-params
  (s/map-of :ring.request.query-params/key :ring.request.query-params/value))


(s/fdef util/t
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :blaze.db/t))


(s/fdef util/page-size
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret nat-int?)


(s/fdef util/page-type
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :fhir.resource/type))


(s/fdef util/page-id
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :blaze.resource/id))


(s/fdef util/type-url
  :args (s/cat :context (s/keys :req [:blaze/base-url ::reitit/router])
               :type :fhir.resource/type)
  :ret string?)


(s/fdef util/instance-url
  :args (s/cat :context (s/keys :req [:blaze/base-url ::reitit/router])
               :type :fhir.resource/type :id :blaze.resource/id)
  :ret string?)


(s/fdef util/versioned-instance-url
  :args (s/cat :context (s/keys :req [:blaze/base-url ::reitit/router])
               :type :fhir.resource/type :id :blaze.resource/id :vid string?)
  :ret string?)
