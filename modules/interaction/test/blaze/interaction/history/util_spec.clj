(ns blaze.interaction.history.util-spec
  (:require
    [blaze.db.api-spec]
    [blaze.handler.fhir.util-spec]
    [blaze.http.spec]
    [blaze.interaction.history.util :as util]
    [blaze.spec]
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
    :context (s/keys :req [:blaze/base-url :blaze/db ::reitit/match])
    :query-params (s/nilable :ring.request/query-params)
    :page-t :blaze.db/t
    :type (s/? :fhir.resource/type)
    :id (s/? :blaze.resource/id)))


(s/fdef util/build-entry
  :args (s/cat :context (s/keys :req [:blaze/base-url ::reitit/router])
               :resource :blaze/resource))
