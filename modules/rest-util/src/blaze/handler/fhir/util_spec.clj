(ns blaze.handler.fhir.util-spec
  (:require
    [blaze.fhir.spec]
    [blaze.handler.fhir.util :as util]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(s/fdef util/t
  :args (s/cat :query-params (s/map-of string? string?))
  :ret (s/nilable nat-int?))


(s/fdef util/page-size
  :args (s/cat :query-params (s/map-of string? string?))
  :ret nat-int?)


(s/fdef util/page-id
  :args (s/cat :query-params (s/map-of string? string?))
  :ret (s/nilable :blaze.resource/id))


(s/fdef util/type-url
  :args (s/cat :router reitit/router? :type :blaze.resource/resourceType)
  :ret string?)


(s/fdef util/instance-url
  :args (s/cat :router reitit/router? :type :blaze.resource/resourceType
               :id :blaze.resource/id)
  :ret string?)
