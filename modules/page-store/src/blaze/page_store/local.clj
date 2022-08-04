(ns blaze.page-store.local
  (:require
    [blaze.anomaly :as ba]
    [blaze.async.comp :as ac]
    [blaze.page-store.local.token :as token]
    [blaze.page-store.protocols :as p]
    [blaze.page-store.spec]
    [blaze.page-store.weigh :as w]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [java-time :as time]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache Cache Caffeine Weigher]))


(set! *warn-on-reflection* true)


(defn- not-found-msg [token]
  (format "Clauses of token `%s` not found." token))


(defrecord LocalPageStore [secure-rng ^Cache db]
  p/PageStore
  (-get [_ token]
    (ac/completed-future
      (or (.getIfPresent db token) (ba/not-found (not-found-msg token)))))

  (-put [_ clauses]
    (if (empty? clauses)
      (ac/completed-future (ba/incorrect "Clauses should not be empty."))
      (let [token (token/generate secure-rng)]
        (.put db token clauses)
        (ac/completed-future token)))))


(def ^:private ^:const ^long token-weigh 72)


(def ^:private weigher
  (reify Weigher
    (weigh [_ _ clauses]
      (+ token-weigh (w/weigh clauses)))))


(defmethod ig/pre-init-spec :blaze.page-store/local [_]
  (s/keys :req-un [:blaze.page-store/secure-rng]))


(defmethod ig/init-key :blaze.page-store/local
  [_ {:keys [secure-rng max-size-in-mb expire-duration]
      :or {max-size-in-mb 10 expire-duration (time/hours 24)}}]
  (log/info "Open local page store")
  (->LocalPageStore
    secure-rng
    (-> (Caffeine/newBuilder)
        (.maximumWeight (* 1024 1024 max-size-in-mb))
        (.weigher weigher)
        (.expireAfterAccess expire-duration)
        (.build))))


(derive :blaze.page-store/local :blaze/page-store)
