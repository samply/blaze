(ns blaze.interaction.history.util-spec
  (:require
    [blaze.db.api-spec]
    [blaze.handler.fhir.util-spec]
    [blaze.interaction.history.util :as util]
    [blaze.interaction.spec]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit]))


(s/fdef util/since
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable inst?))


(s/fdef util/page-t
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :blaze.db/t))


(s/fdef util/nav-url
  :args
  (s/cat
    :base-url string?
    :match :fhir.router/match
    :query-params (s/nilable :ring.request/query-params)
    :t :blaze.db/t
    :page-t :blaze.db/t
    :type (s/? :fhir.type/name)
    :id (s/? :blaze.resource/id)))


(s/fdef util/build-entry
  :args (s/cat :base-url string? :router reitit/router?
               :resource :blaze/resource))
