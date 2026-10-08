(ns blaze.page-store.cache
  "The in-memory cache of clauses shared by the page store implementations.

  Consists of a clause cache and a token cache. Each clause is cached
  separately under its hash, so clauses are shared between tokens. A token
  refers to the hashes of its clauses."
  (:refer-clojure :exclude [get])
  (:require
   [blaze.metrics.core :as metrics]
   [blaze.page-store.hash :as hash])
  (:import
   [com.github.benmanes.caffeine.cache Cache Caffeine]
   [com.google.common.hash HashCode]
   [com.google.common.io BaseEncoding]))

(set! *warn-on-reflection* true)

(defn create
  "Creates a cache with entries expiring `expire-duration` after last access."
  [expire-duration]
  (-> (Caffeine/newBuilder)
      (.expireAfterAccess expire-duration)
      (.build)))

(defn- decode-token [token]
  (HashCode/fromBytes (.decode (BaseEncoding/base16) ^String token)))

(defn get
  "Returns the clauses cached under `token` or nil if at least one of them is
  missing."
  [^Cache clause-cache ^Cache token-cache token]
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

(defn- store! [^Cache clause-cache ^Cache token-cache hash {:keys [hashes clauses]}]
  (run! (fn [[hash clause]] (.get clause-cache hash (fn [_] clause))) clauses)
  (.get token-cache hash (fn [_] hashes)))

(defn put!
  "Caches `clauses` under the token generated from them and returns that token."
  [clause-cache token-cache clauses]
  (let [{:keys [hash] :as hashed} (hash/hash-clauses clauses)]
    (store! clause-cache token-cache hash hashed)
    (hash/encode hash)))

(defn put-under-token!
  "Caches `clauses` under `token`, which doesn't have to be the token `put!`
  would generate from them."
  [clause-cache token-cache token clauses]
  (store! clause-cache token-cache (decode-token token)
          (hash/hash-clauses clauses))
  nil)

(defn- estimated-size [type ^Cache cache]
  {:label-values [type] :value (.estimatedSize cache)})

(defn collector
  "Creates a collector of the estimated sizes of both caches."
  [clause-cache token-cache]
  (metrics/collector
    [(metrics/gauge-metric
      "blaze_page_store_estimated_size"
      "Returns the approximate number of entries in the page store."
      ["type"]
      [(estimated-size "token" token-cache)
       (estimated-size "clause" clause-cache)])]))
