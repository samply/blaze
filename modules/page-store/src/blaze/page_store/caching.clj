(ns blaze.page-store.caching
  "A caching implementation of a page store in front of a backing store.

  Generates the tokens and acts as a write-through cache. Clauses missing in
  the cache are loaded from the backing store."
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.module :as m]
   [blaze.page-store :as-alias page-store]
   [blaze.page-store.backing-store :as backing-store]
   [blaze.page-store.cache :as cache]
   [blaze.page-store.caching.spec]
   [blaze.page-store.protocols :as p]
   [blaze.page-store.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log]))

(defrecord CachingPageStore [clause-cache token-cache backing-store]
  p/PageStore
  (-get [_ token]
    (if-some [clauses (cache/get clause-cache token-cache token)]
      (ac/completed-future clauses)
      (do-sync [clauses (backing-store/get backing-store token)]
        (cache/put-under-token! clause-cache token-cache token clauses)
        clauses)))

  (-put [_ clauses]
    (let [token (cache/put! clause-cache token-cache clauses)]
      (do-sync [_ (backing-store/put! backing-store token clauses)]
        token))))

(defmethod m/pre-init-spec ::page-store/caching [_]
  (s/keys :req-un [::backing-store] :opt-un [::expire-duration]))

(defmethod ig/init-key ::page-store/caching
  [_ {:keys [backing-store expire-duration]
      :or {expire-duration (time/hours 1)}}]
  (log/info "Open caching page store with an expire duration of"
            (str expire-duration))
  (->CachingPageStore (cache/create expire-duration)
                      (cache/create expire-duration)
                      backing-store))

(derive ::page-store/caching :blaze/page-store)

(defmethod m/pre-init-spec ::collector [_]
  (s/keys :req-un [:blaze/page-store]))

(defmethod ig/init-key ::collector
  [_ {{:keys [clause-cache token-cache]} :page-store}]
  (cache/collector clause-cache token-cache))

(derive ::collector :blaze.metrics/collector)
