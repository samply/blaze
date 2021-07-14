(ns blaze.db.tx-cache
  "A cache for transactions.

  Caffeine is used because it have better performance characteristics as a
  ConcurrentHashMap."
  (:require
    [blaze.db.impl.index.tx-success :as tx-success]
    [blaze.db.kv.spec]
    [blaze.db.tx-cache.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine]))


(set! *warn-on-reflection* true)


(defn- new-tx-cache
  "Creates a new transaction cache."
  [kv-store max-size]
  (-> (Caffeine/newBuilder)
      (.maximumSize max-size)
      (.recordStats)
      (.build (tx-success/cache-loader kv-store))))


(defmethod ig/pre-init-spec :blaze.db/tx-cache [_]
  (s/keys :req-un [:blaze.db/kv-store] :opt-un [::max-size]))


(defmethod ig/init-key :blaze.db/tx-cache
  [_ {:keys [kv-store max-size] :or {max-size 0}}]
  (log/info "Create transaction cache with a size of" max-size "transactions")
  (new-tx-cache kv-store max-size))
