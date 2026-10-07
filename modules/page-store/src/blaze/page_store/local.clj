(ns blaze.page-store.local
  (:refer-clojure :exclude [load])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.metrics.core :as metrics]
   [blaze.module :as m]
   [blaze.page-store :as page-store]
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

(defn- load [^Cache clause-cache ^Cache token-cache token]
  (some->>
   (.getIfPresent token-cache (decode-token token))
   (reduce
    (fn [ret hashes]
      (if-some [clauses (reduce
                         #(if-some [clause (.getIfPresent clause-cache %2)]
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

(defn- store [clause-cache ^Cache token-cache clauses]
  (let [hashes (mapv (partial store-disjunction clause-cache) clauses)
        hash (hash/hash-hashes hashes)]
    (.get token-cache hash (fn [_] hashes))
    (hash/encode hash)))

(defrecord LocalPageStore [clause-cache token-cache backing-store]
  p/PageStore
  (-get [_ token]
    (if-some [clauses (load clause-cache token-cache token)]
      (ac/completed-future clauses)
      (if backing-store
        (-> (page-store/get backing-store token)
            (ac/then-apply
             (fn [clauses]
               (store clause-cache token-cache clauses)
               clauses)))
        (ac/completed-future (ba/not-found (not-found-msg token))))))

  (-put [_ clauses]
    (if (empty? clauses)
      (ac/completed-future (ba/incorrect "Clauses should not be empty."))
      (let [token (store clause-cache token-cache clauses)]
        (if backing-store
          (-> (page-store/put! backing-store clauses)
              (ac/then-apply (fn [_] token)))
          (ac/completed-future token))))))

(defn- cache [expire-duration]
  (-> (Caffeine/newBuilder)
      (.expireAfterAccess expire-duration)
      (.build)))

(defmethod m/pre-init-spec ::page-store/local [_]
  (s/keys :opt-un [::expire-duration ::backing-store]))

(defmethod ig/init-key ::page-store/local
  [_ {:keys [expire-duration backing-store] :or {expire-duration (time/hours 5)}}]
  (log/info "Open local page store with an expire duration of"
            (str expire-duration))
  (->LocalPageStore (cache expire-duration) (cache expire-duration)
                    backing-store))

(defmethod m/pre-init-spec :blaze.page-store.local/collector [_]
  (s/keys :req-un [:blaze/page-store]))

(defn- estimated-size [type ^Cache cache]
  {:label-values [type] :value (.estimatedSize cache)})

(defmethod ig/init-key :blaze.page-store.local/collector
  [_ {{:keys [token-cache clause-cache]} :page-store}]
  (metrics/collector
    [(metrics/gauge-metric
      "blaze_page_store_estimated_size"
      "Returns the approximate number of entries in the page store."
      ["type"]
      [(estimated-size "token" token-cache)
       (estimated-size "clause" clause-cache)])]))

(derive :blaze.page-store.local/collector :blaze.metrics/collector)
