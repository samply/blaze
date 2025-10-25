(ns blaze.interaction.history.util-spec
  (:require
   [blaze.db.api-spec]
   [blaze.handler.fhir.util-spec]
   [blaze.http.spec]
   [blaze.interaction.history.util :as util]
   [blaze.interaction.search.util :as-alias search-util]
   [blaze.module-spec]
   [blaze.spec]
   [blaze.util-spec]
   [clojure.spec.alpha :as s]
   [reitit.core :as reitit]))

(s/fdef util/since
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable inst?))

(s/fdef util/page-t
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :blaze.db/t))

(s/fdef util/self-link
  :args
  (s/cat
   :context (s/keys :req [::search-util/link :blaze/base-url ::reitit/match])
   :query-params (s/nilable :ring.request/query-params)))

(s/fdef util/page-nav-url
  :args
  (s/cat
   :context (s/keys :req [:blaze/base-url :blaze/db ::reitit/match])
   :query-params (s/nilable :ring.request/query-params)
   :page-t :blaze.db/t
   :type (s/? :fhir.resource/type)
   :id (s/? :blaze.resource/id))
  :ret string?)

(s/fdef util/build-entry
  :args (s/cat :context (s/keys :req [:blaze/base-url ::reitit/router])
               :resource :fhir/Resource))

(s/fdef util/build-bundle
  :args (s/cat
         :context (s/keys :req [::search-util/link :blaze/base-url ::reitit/match])
         :total nat-int?
         :query-params (s/nilable :ring.request/query-params)))
