(ns blaze.terminology-service.extern
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir-client :as fhir-client]
   [blaze.fhir.parsing-context.spec]
   [blaze.http-client.spec]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.terminology-service :as ts]
   [blaze.terminology-service.extern.spec]
   [blaze.terminology-service.protocols :as p]
   [clojure.spec.alpha :as s]
   [integrant.core :as ig]
   [java-time.api :as time]
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
  (take 14 (iterate #(* 2 %) 0.001))
  "op")

(defn- expand-value-set [base-uri http-opts params]
  (let [timer (prom/timer request-duration-seconds "expand-value-set")]
    (-> (fhir-client/execute-type-post base-uri "ValueSet" "expand" params http-opts)
        (ac/when-complete
         (fn [result _]
           (log/trace "Valueset $expand result: " result)
           (prom/observe-duration! timer))))))

(defn- value-set-validate-code [base-uri http-opts params]
  (let [timer (prom/timer request-duration-seconds "value-set-validate-code")]
    (-> (fhir-client/execute-type-post base-uri "ValueSet" "validate-code" params http-opts)
        (ac/when-complete
         (fn [result _]
           (log/trace "Valueset $validate-code result: " result)
           (prom/observe-duration! timer))))))

(defn- value-set-validate-code-cache [base-uri http-opts]
  (-> (Caffeine/newBuilder)
      (.maximumSize 1000)
      (.refreshAfterWrite (time/hours 1))
      (^[AsyncCacheLoader] Caffeine/.buildAsync
       (fn [params _]
         (value-set-validate-code base-uri http-opts params)))))

(defmethod m/pre-init-spec ::ts/extern [_]
  (s/keys :req-un [::base-uri :blaze/http-client :blaze.fhir/parsing-context
                   :blaze.fhir/writing-context]))

(defmethod ig/init-key ::ts/extern
  [_ {:keys [base-uri http-client parsing-context writing-context]}]
  (log/info (str "Init terminology server connection: " base-uri))
  (let [http-opts {:http-client http-client
                   :parsing-context parsing-context
                   :writing-context writing-context}
        value-set-validate-code-cache (value-set-validate-code-cache base-uri http-opts)]
    (reify p/TerminologyService
      (-expand-value-set [_ params]
        (expand-value-set base-uri http-opts params))

      (-value-set-validate-code [_ params]
        (.get ^AsyncLoadingCache value-set-validate-code-cache params)))))

(derive ::ts/extern :blaze/terminology-service)

(reg-collector ::request-duration-seconds
  request-duration-seconds)
