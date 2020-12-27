(ns blaze.terminology-service.extern
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir-client :as fhir-client]
    [blaze.module :refer [reg-collector]]
    [blaze.terminology-service :as ts]
    [blaze.terminology-service.extern.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine AsyncCacheLoader
                                        AsyncLoadingCache]))


(set! *warn-on-reflection* true)


(defhistogram request-duration-seconds
  "Terminology Service request latencies in seconds."
  {:namespace "terminology_service"}
  (take 14 (iterate #(* 2 %) 0.001)))


(defn- expand-value-set* [base-uri http-client params]
  (log/debug "Expand ValueSet with params" (pr-str params))
  (fhir-client/execute-type-get base-uri "ValueSet" "expand"
                                {:http-client http-client
                                 :query-params params}))


(defn- expand-value-set [base-uri http-client params]
  (let [timer (prom/timer request-duration-seconds)]
    (-> (expand-value-set* base-uri http-client params)
        (ac/when-complete
          (fn [_ _]
            (prom/observe-duration! timer))))))


(defn- cache [base-uri http-client]
  (-> (Caffeine/newBuilder)
      (.maximumSize 1000)
      (.buildAsync
        (reify AsyncCacheLoader
          (asyncLoad [_ params _]
            (expand-value-set base-uri http-client params))))))


(defrecord TerminologyService [^AsyncLoadingCache cache]
  ts/TerminologyService
  (-expand-value-set [_ params]
    (.get cache params)))


(defn- terminology-service [base-uri http-client]
  (->TerminologyService (cache base-uri http-client)))


(defmethod ig/pre-init-spec :blaze.terminology-service/extern [_]
  (s/keys :req-un [::base-uri ::http-client]))


(defmethod ig/init-key :blaze.terminology-service/extern
  [_ {:keys [base-uri http-client]}]
  (log/info (str "Init terminology server connection: " base-uri))
  (terminology-service base-uri http-client))


(derive :blaze.terminology-service/extern :blaze/terminology-service)


(reg-collector ::request-duration-seconds
  request-duration-seconds)
