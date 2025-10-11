(ns blaze.terminology-service.extern
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir-client :as fhir-client]
   [blaze.fhir.parsing-context.spec]
   [blaze.fhir.spec.type :as type]
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
  "Extern terminology service request latencies."
  {:namespace "blaze"
   :subsystem "terminology_service_extern"}
  (take 14 (iterate #(* 2 %) 0.001)))

(defn- expand-value-set* [base-uri http-client parsing-context writing-context url]
  (log/debug "Expand ValueSet with url" url)
  (fhir-client/execute-type-get base-uri "ValueSet" "expand"
                                {:http-client http-client
                                 :parsing-context parsing-context
                                 :writing-context writing-context
                                 :query-params {:url url}}))

(defn- extract-url [{parameters :parameter}]
  (some #(when (= "url" (type/value (:name %))) (type/value (:value %)))
        parameters))

(defn- expand-value-set [base-uri http-client parsing-context writing-context params]
  (let [timer (prom/timer request-duration-seconds)]
    (-> (expand-value-set* base-uri http-client parsing-context writing-context
                           (extract-url params))
        (ac/when-complete
         (fn [_ _] (prom/observe-duration! timer))))))

(defn- cache [base-uri http-client parsing-context writing-context]
  (-> (Caffeine/newBuilder)
      (.maximumSize 1000)
      (^[AsyncCacheLoader] Caffeine/.buildAsync
       (fn [params _]
         (expand-value-set base-uri http-client parsing-context writing-context params)))))

(defmethod m/pre-init-spec ::ts/extern [_]
  (s/keys :req-un [::base-uri :blaze/http-client :blaze.fhir/parsing-context
                   :blaze.fhir/writing-context]))

(defmethod ig/init-key ::ts/extern
  [_ {:keys [base-uri http-client parsing-context writing-context]}]
  (log/info (str "Init terminology server connection: " base-uri))
  (let [cache (cache base-uri http-client parsing-context writing-context)]
    (reify p/TerminologyService
      (-expand-value-set [_ params]
        (.get ^AsyncLoadingCache cache params)))))

(derive ::ts/extern :blaze/terminology-service)

(reg-collector ::request-duration-seconds
  request-duration-seconds)
