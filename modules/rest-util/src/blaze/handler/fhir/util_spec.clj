(ns blaze.handler.fhir.util-spec
  (:require
   [blaze.fhir.spec]
   [blaze.handler.fhir.util :as util]
   [blaze.handler.fhir.util.spec]
   [blaze.http.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [reitit.core :as reitit]))

(s/fdef util/t
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :blaze.db/t))

(s/fdef util/page-size
  :args (s/cat :query-params (s/nilable :ring.request/query-params)
               :max (s/? pos-int?) :default (s/? (s/nilable pos-int?)))
  :ret nat-int?)

(s/fdef util/page-offset
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret nat-int?)

(s/fdef util/page-type
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :fhir.resource/type))

(s/fdef util/page-id
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :blaze.resource/id))

(s/fdef util/elements
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable (s/coll-of simple-keyword?)))

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
