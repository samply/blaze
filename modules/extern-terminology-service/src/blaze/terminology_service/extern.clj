(ns blaze.terminology-service.extern
  (:require
   [blaze.anomaly :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.fhir-client :as fhir-client]
   [blaze.fhir.parsing-context.spec]
   [blaze.http-client.spec]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.openid-client.token-provider :as tp]
   [blaze.openid-client.token-provider.spec]
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

(defn- with-oauth-token [http-opts provider]
  (when-ok [token (tp/current-token provider)]
    (assoc http-opts :oauth-token token)))

(defn- prep-http-opts [token-provider http-opts]
  (cond-> http-opts
    token-provider (with-oauth-token token-provider)))

(defn- code-system-validate-code [base-uri http-opts params]
  (let [timer (prom/timer request-duration-seconds "code-system-validate-code")]
    (-> (fhir-client/execute-type-post base-uri "CodeSystem" "validate-code" params http-opts)
        (ac/when-complete
         (fn [result _]
           (log/trace "CodeSystem $validate-code result: " result)
           (prom/observe-duration! timer))))))

(defn- code-system-validate-code-cache [base-uri http-opts token-provider]
  (-> (Caffeine/newBuilder)
      (.maximumSize 1000)
      (.refreshAfterWrite (time/hours 1))
      (^[AsyncCacheLoader] Caffeine/.buildAsync
       (fn [params _]
         (if-ok [http-opts (prep-http-opts token-provider http-opts)]
           (code-system-validate-code base-uri http-opts params)
           ac/completed-future)))))

(defn- expand-value-set [base-uri http-opts token-provider params]
  (let [timer (prom/timer request-duration-seconds "expand-value-set")]
    (-> (if-ok [http-opts (prep-http-opts token-provider http-opts)]
          (fhir-client/execute-type-post base-uri "ValueSet" "expand" params http-opts)
          ac/completed-future)
        (ac/when-complete
         (fn [result _]
           (log/trace "ValueSet $expand result: " result)
           (prom/observe-duration! timer))))))

(defn- value-set-validate-code [base-uri http-opts params]
  (let [timer (prom/timer request-duration-seconds "value-set-validate-code")]
    (-> (fhir-client/execute-type-post base-uri "ValueSet" "validate-code" params http-opts)
        (ac/when-complete
         (fn [result _]
           (log/trace "ValueSet $validate-code result: " result)
           (prom/observe-duration! timer))))))

(defn- value-set-validate-code-cache [base-uri http-opts token-provider]
  (-> (Caffeine/newBuilder)
      (.maximumSize 1000)
      (.refreshAfterWrite (time/hours 1))
      (^[AsyncCacheLoader] Caffeine/.buildAsync
       (fn [params _]
         (if-ok [http-opts (prep-http-opts token-provider http-opts)]
           (value-set-validate-code base-uri http-opts params)
           ac/completed-future)))))

(defmethod m/pre-init-spec ::ts/extern [_]
  (s/keys :req-un [::base-uri :blaze/http-client :blaze.fhir/parsing-context
                   :blaze.fhir/writing-context]
          :opt-un [:blaze.openid-client/token-provider]))

(defmethod ig/init-key ::ts/extern
  [_ {:keys [base-uri http-client parsing-context writing-context token-provider]}]
  (log/info (str "Init terminology server connection: " base-uri))
  (let [http-opts {:http-client http-client
                   :parsing-context parsing-context
                   :writing-context writing-context}
        code-system-validate-code-cache (code-system-validate-code-cache base-uri http-opts token-provider)
        value-set-validate-code-cache (value-set-validate-code-cache base-uri http-opts token-provider)]
    (reify p/TerminologyService
      (-code-system-validate-code [_ params]
        (.get ^AsyncLoadingCache code-system-validate-code-cache params))

      (-expand-value-set [_ params]
        (expand-value-set base-uri http-opts token-provider params))

      (-value-set-validate-code [_ params]
        (.get ^AsyncLoadingCache value-set-validate-code-cache params)))))

(reg-collector ::request-duration-seconds
  request-duration-seconds)
