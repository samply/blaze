(ns blaze.page-store.local
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.metrics.core :as metrics]
   [blaze.module :as m]
   [blaze.page-store :as-alias page-store]
   [blaze.page-store.local.spec]
   [blaze.page-store.local.token :as token]
   [blaze.page-store.protocols :as p]
   [blaze.page-store.spec]
   [blaze.page-store.weigh :as w]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache Cache Caffeine Weigher]))

(set! *warn-on-reflection* true)

(defn- not-found-msg [token]
  (format "Clauses of token `%s` not found." token))

(defrecord LocalPageStore [secure-rng ^Cache cache]
  p/PageStore
  (-get [_ token]
    (ac/completed-future
     (or (.getIfPresent cache token) (ba/not-found (not-found-msg token)))))

  (-put [_ clauses]
    (if (empty? clauses)
      (ac/completed-future (ba/incorrect "Clauses should not be empty."))
      (let [token (token/generate secure-rng)]
        (.put cache token clauses)
        (ac/completed-future token)))))

(def ^:private ^:const ^long token-weigh 72)

(def ^:private weigher
  (reify Weigher
    (weigh [_ _ clauses]
      (+ token-weigh (w/weigh clauses)))))

(defmethod m/pre-init-spec ::page-store/local [_]
  (s/keys :req-un [::page-store/secure-rng]
          :opt-un [::max-size-in-mb ::expire-duration]))

(defmethod ig/init-key ::page-store/local
  [_ {:keys [secure-rng max-size-in-mb expire-duration]
      :or {max-size-in-mb 10 expire-duration (time/hours 24)}}]
  (log/info "Open local page store with a capacity of" max-size-in-mb
            "MiB and an expire duration of" expire-duration)
  (->LocalPageStore
   secure-rng
   (-> (Caffeine/newBuilder)
       (.maximumWeight (bit-shift-left max-size-in-mb 20))
       (.weigher weigher)
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
