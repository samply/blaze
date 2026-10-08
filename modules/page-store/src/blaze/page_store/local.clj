(ns blaze.page-store.local
  "A local, in-memory implementation of a page store.

  Generates the tokens and keeps the clauses in memory only."
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.module :as m]
   [blaze.page-store :as-alias page-store]
   [blaze.page-store.cache :as cache]
   [blaze.page-store.local.spec]
   [blaze.page-store.protocols :as p]
   [blaze.page-store.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log]))

(defn- not-found-msg [token]
  (format "Clauses of token `%s` not found." token))

(defrecord LocalPageStore [clause-cache token-cache]
  p/PageStore
  (-get [_ token]
    (ac/completed-future
     (if-some [clauses (cache/get clause-cache token-cache token)]
       clauses
       (ba/not-found (not-found-msg token)))))

  (-put [_ clauses]
    (ac/completed-future (cache/put! clause-cache token-cache clauses))))

(defmethod m/pre-init-spec ::page-store/local [_]
  (s/keys :opt-un [::expire-duration]))

(defmethod ig/init-key ::page-store/local
  [_ {:keys [expire-duration] :or {expire-duration (time/hours 5)}}]
  (log/info "Open local page store with an expire duration of"
            (str expire-duration))
  (->LocalPageStore (cache/create expire-duration)
                    (cache/create expire-duration)))

(derive ::page-store/local :blaze/page-store)

(defmethod m/pre-init-spec ::collector [_]
  (s/keys :req-un [:blaze/page-store]))

(defmethod ig/init-key ::collector
  [_ {{:keys [clause-cache token-cache]} :page-store}]
  (cache/collector clause-cache token-cache))

(derive ::collector :blaze.metrics/collector)
