(ns blaze.terminology-service.extern
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir-client :as fhir-client]
   [blaze.http-client.spec]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.extern.spec]
   [blaze.terminology-service.protocols :as p]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [prometheus.alpha :as prom :refer [defhistogram]]
   [taoensso.timbre :as log])
  (:import
   [com.github.benmanes.caffeine.cache
    AsyncCacheLoader AsyncLoadingCache Caffeine]))

(set! *warn-on-reflection* true)

(defhistogram request-duration-seconds
  "Terminology Service request latencies in seconds."
  {:namespace "terminology_service"}
  (take 14 (iterate #(* 2 %) 0.001)))

(defn- expand-value-set* [base-uri http-client url]
  (log/debug "Expand ValueSet with url" url)
  (fhir-client/execute-type-get base-uri "ValueSet" "expand"
                                {:http-client http-client
                                 :query-params {:url url}}))

(defn- expand-value-set [base-uri http-client url]
  (let [timer (prom/timer request-duration-seconds)]
    (-> (expand-value-set* base-uri http-client url)
        (ac/when-complete
         (fn [_ _] (prom/observe-duration! timer))))))

(defn- cache [base-uri http-client]
  (-> (Caffeine/newBuilder)
      (.maximumSize 1000)
      (^[AsyncCacheLoader] Caffeine/.buildAsync
       (fn [request _]
         (expand-value-set base-uri http-client (:url request))))))

(defmethod m/pre-init-spec ::ts/extern [_]
  (s/keys :req-un [::base-uri :blaze/http-client]))

(defmethod ig/init-key ::ts/extern
  [_ {:keys [base-uri http-client]}]
  (log/info (str "Init terminology server connection: " base-uri))
  (let [cache (cache base-uri http-client)]
    (reify p/TerminologyService
      (-expand-value-set [_ request]
        (.get ^AsyncLoadingCache cache request)))))

(derive ::ts/extern :blaze/terminology-service)

(reg-collector ::request-duration-seconds
  request-duration-seconds)
