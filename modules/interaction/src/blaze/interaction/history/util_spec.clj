(ns blaze.interaction.history.util-spec
  (:require
    [blaze.db.api-spec]
    [blaze.interaction.history.util :as util]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(s/fdef util/since-inst
  :args (s/cat :query-params (s/map-of string? string?))
  :ret (s/nilable inst?))


(s/fdef util/page-t
  :args (s/cat :query-params (s/map-of string? string?))
  :ret (s/nilable :blaze.db/t))


(s/fdef util/page-type
  :args (s/cat :query-params (s/map-of string? string?))
  :ret (s/nilable :blaze.resource/resourceType))


(s/fdef util/page-id
  :args (s/cat :query-params (s/map-of string? string?))
  :ret (s/nilable :blaze.resource/id))


(s/fdef util/nav-url
  :args
  (s/cat
    :match :fhir.router/match
    :query-params (s/map-of string? string?)
    :t :blaze.db/t
    :page-t :blaze.db/t
    :type (s/? :blaze.resource/resourceType)
    :id (s/? :blaze.resource/id)))


(s/fdef util/build-entry
  :args (s/cat :router reitit/router? :resource :blaze/resource))
