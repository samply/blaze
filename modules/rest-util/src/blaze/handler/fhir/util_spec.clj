(ns blaze.handler.fhir.util-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.fhir.spec]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.fhir.util.spec]
   [blaze.http.spec]
   [blaze.rest-api :as-alias rest-api]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [reitit.core :as reitit]))

(s/fdef fhir-util/parse-nat-long
  :args (s/cat :s string?)
  :ret (s/nilable nat-int?))

(s/fdef fhir-util/t
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :blaze.db/t))

(s/fdef fhir-util/page-size
  :args (s/cat :query-params (s/nilable :ring.request/query-params)
               :max (s/? pos-int?) :default (s/? (s/nilable pos-int?)))
  :ret nat-int?)

(s/fdef fhir-util/page-offset
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret nat-int?)

(s/fdef fhir-util/page-type
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :fhir.resource/type))

(s/fdef fhir-util/page-id
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable :blaze.resource/id))

(s/fdef fhir-util/summary
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret #{:complete :summary})

(s/fdef fhir-util/elements
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable (s/coll-of simple-keyword?)))

(s/fdef fhir-util/since
  :args (s/cat :query-params (s/nilable :ring.request/query-params))
  :ret (s/nilable inst?))

(s/fdef fhir-util/date
  :args (s/cat :query-params (s/nilable :ring.request/query-params)
               :name string?)
  :ret (s/or :date :system/date :anomaly ::anom/anomaly))

(s/fdef fhir-util/instance-url
  :args (s/cat :context (s/keys :req [:blaze/base-url ::reitit/router])
               :type :fhir.resource/type :id :blaze.resource/id)
  :ret string?)

(s/fdef fhir-util/versioned-instance-url
  :args (s/cat :context (s/keys :req [:blaze/base-url ::reitit/router])
               :type :fhir.resource/type :id :blaze.resource/id :vid string?)
  :ret string?)

(s/fdef fhir-util/last-modified
  :args (s/cat :tx :blaze.db/tx)
  :ret string?)

(s/fdef fhir-util/etag
  :args (s/cat :tx :blaze.db/tx)
  :ret string?)

(s/fdef fhir-util/resource-handle
  :args (s/cat :db :blaze.db/db :type :fhir.resource/type :id :blaze.resource/id)
  :ret ac/completable-future?)

(s/fdef fhir-util/pull
  :args (s/cat :db :blaze.db/db :type :fhir.resource/type :id :blaze.resource/id
               :variant (s/? :blaze.resource/variant))
  :ret ac/completable-future?)

(s/fdef fhir-util/pull-historic
  :args (s/cat :db :blaze.db/db :type :fhir.resource/type :id :blaze.resource/id
               :t :blaze.db/t)
  :ret ac/completable-future?)

(s/fdef fhir-util/sync
  :args (s/cat :node :blaze.db/node :t (s/? :blaze.db/t)
               :timeout ::rest-api/db-sync-timeout)
  :ret ac/completable-future?)

(s/fdef fhir-util/match-type-id
  :args (s/cat :url string?)
  :ret (s/nilable (s/tuple :fhir.resource/type :blaze.resource/id)))

(s/fdef fhir-util/match-type-query-params
  :args (s/cat :url string?)
  :ret (s/nilable (s/tuple :fhir.resource/type (s/nilable string?))))

(s/fdef fhir-util/match-url
  :args (s/cat :url string?)
  :ret map?)

(s/fdef fhir-util/validate-entry
  :args (s/cat :idx nat-int? :entry :fhir.Bundle/entry)
  :ret (s/or :entry :fhir.Bundle/entry :anomaly ::anom/anomaly))

(s/fdef fhir-util/process-batch-entry
  :args (s/cat :context (s/keys :req [:blaze/base-url]
                                :req-un [::rest-api/batch-handler]
                                :opt [:blaze/db :blaze/cancelled?
                                      :blaze.preference/return])
               :idx nat-int? :entry :fhir.Bundle/entry)
  :ret (s/or :response-entry :fhir.Bundle/entry :anomaly ::anom/anomaly))

(s/fdef fhir-util/process-batch-entries
  :args (s/cat :context (s/keys :req [:blaze/base-url]
                                :req-un [::rest-api/batch-handler]
                                :opt [:blaze/db :blaze/cancelled?
                                      :blaze.preference/return])
               :entries (s/coll-of :fhir.Bundle/entry))
  :ret (s/or :response-entry :fhir.Bundle/entry :anomaly ::anom/anomaly))
