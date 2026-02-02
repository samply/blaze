(ns blaze.page-store.local
  (:refer-clojure :exclude [load])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.metrics.core :as metrics]
   [blaze.module :as m]
   [blaze.page-store :as-alias page-store]
   [blaze.page-store.local.hash :as hash]
   [blaze.page-store.local.spec]
   [blaze.page-store.protocols :as p]
   [blaze.page-store.spec]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache Cache Caffeine]
   [com.google.common.hash HashCode]
   [com.google.common.io BaseEncoding]))

(set! *warn-on-reflection* true)

(defn- not-found-msg [token]
  (format "Clauses of token `%s` not found." token))

(defn- decode-token [token]
  (HashCode/fromBytes (.decode (BaseEncoding/base16) ^String token)))

(defn- load [^Cache cache token]
  (some->>
   (.getIfPresent cache (decode-token token))
   (reduce
    (fn [ret hashes]
      (if-some [clauses (reduce
                         #(if-some [clause (.getIfPresent cache %2)]
                            (conj %1 clause)
                            (reduced nil))
                         []
                         hashes)]
        (conj ret (cond-> clauses (= 1 (count clauses)) first))
        (reduced nil)))
    [])))

(defn- store-clause [^Cache cache clause]
  (let [hash (hash/hash-clause clause)]
    (.get cache hash (fn [_] clause))
    hash))

(defn- multiple-clauses? [disjunction]
  (sequential? (first disjunction)))

(defn- store-disjunction [cache disjunction]
  (if (multiple-clauses? disjunction)
    (mapv (partial store-clause cache) disjunction)
    [(store-clause cache disjunction)]))

(defn- store [^Cache cache clauses]
  (let [hashes (mapv (partial store-disjunction cache) clauses)
        hash (hash/hash-hashes hashes)]
    (.get cache hash (fn [_] hashes))
    (hash/encode hash)))

(defrecord LocalPageStore [cache]
  p/PageStore
  (-get [_ token]
    (ac/completed-future
     (or (load cache token) (ba/not-found (not-found-msg token)))))

  (-put [_ clauses]
    (ac/completed-future
     (if (empty? clauses)
       (ba/incorrect "Clauses should not be empty.")
       (store cache clauses)))))

(defmethod m/pre-init-spec ::page-store/local [_]
  (s/keys :opt-un [::expire-duration]))

(defmethod ig/init-key ::page-store/local
  [_ {:keys [expire-duration] :or {expire-duration (time/hours 1)}}]
  (log/info "Open local page store with an expire duration of"
            (str expire-duration))
  (->LocalPageStore
   (-> (Caffeine/newBuilder)
       (.expireAfterAccess expire-duration)
       (.build))))

(derive ::page-store/local :blaze/page-store)

(defmethod m/pre-init-spec :blaze.page-store.local/collector [_]
  (s/keys :req-un [:blaze/page-store]))

(defmethod ig/init-key :blaze.page-store.local/collector
  [_ {:keys [page-store]}]
  (metrics/collector
    [(metrics/gauge-metric
      "blaze_page_store_estimated_size"
      "Returns the approximate number of tokens in the page store."
      []
      [{:label-values []
        :value (.estimatedSize ^Cache (:cache page-store))}])]))

(derive :blaze.page-store.local/collector :blaze.metrics/collector)
