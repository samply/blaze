(ns blaze.fhir.operation.cql
  "Main entry point into the $cql operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.cql.translator :as cql-translator]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.expression :as expr]
   [blaze.elm.resource :as cr]
   [blaze.executors :as ex]
   [blaze.fhir.operation.cql.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.util :as fu]
   [blaze.handler.util :as handler-util]
   [blaze.job.async-interaction.request :as req]
   [blaze.module :as m :refer [reg-collector]]
   [blaze.util :as u]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [java-time.api :as time]
   [prometheus.alpha :as prom]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [blaze.elm.resource Resource]
   [blaze.fhir.spec.type.system Date]
   [java.util List Map]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(prom/defhistogram compile-duration-seconds
  "$cql compiling latencies in seconds."
  {:namespace "fhir"
   :subsystem "cql"}
  (take 12 (iterate #(* 2 %) 0.001)))

(prom/defhistogram evaluate-duration-seconds
  "$cql evaluating latencies in seconds."
  {:namespace "fhir"
   :subsystem "cql"}
  (take 16 (iterate #(* 2 %) 0.1)))

(def ^:private param-specs
  {"expression" {:action :copy}
   "parameters" {:action :copy-resource}})

(defn- fhir->cql [value]
  {:type "System.Integer" :value value})

(defn- coerce-expr-params [{params :parameter}]
  (reduce
   (fn [res {{name :value} :name {value :value} :value}]
     (if-ok [value (fhir->cql value)]
       (if-let [existing-value (res name)]
         (assoc res name (conj (u/to-seq existing-value) value))
         (assoc res name value))
       reduced))
   {}
   params))

(def ^:private cql-library-template
  "library CQL
   using FHIR version '4.0.0'
   include FHIRHelpers version '4.0.0'

   %s

   context Unfiltered

   define Expression: %s")

(defn- cql-parameter [[name {:keys [type]}]]
  (format "parameter %s %s" name type))

(defn- cql-parameters [parameters]
  (str/join "\n" (map cql-parameter parameters)))

(defn- create-cql-library
  "Creates the CQL Library as string."
  [parameters expression]
  (format cql-library-template (cql-parameters parameters) expression))

(defn- translate [cql-code]
  (-> (cql-translator/translate cql-code)
      (ba/exceptionally #(assoc % :fhir/issue "value"))))

(defn- extract-param-value [parameters]
  (into {} (map (fn [[name {:keys [value]}]] [name value])) parameters))

(defn- compile-library* [context expression parameters]
  (when-ok [cql-library (create-cql-library parameters expression)
            elm-library (translate cql-library)
            {:keys [expression-defs]} (library/compile-library context elm-library {})]
    (cond-> expression-defs
      (seq parameters) (library/resolve-params (extract-param-value parameters)))))

(defn- compile-library [context expression parameters]
  (with-open [_ (prom/timer compile-duration-seconds)]
    (compile-library* context expression parameters)))

(defn- evaluate-expression-error-msg [e]
  (format "Error while evaluating the expression: %s" (ex-message e)))

(defn- evaluate-expression* [context expression]
  (try
    (expr/eval context expression nil)
    (catch Exception e
      (let [ex-data (ex-data e)]
        ;; only log if the exception hasn't ex-data because exception with
        ;; ex-data are controlled by us and so are not unexpected
        (when-not ex-data
          (log/error (evaluate-expression-error-msg e))
          (log/error e))
        (-> (ba/fault
             (evaluate-expression-error-msg e)
             :fhir/issue "exception")
            (merge ex-data))))))

(defn- evaluate-expression [context expression]
  (with-open [_ (prom/timer evaluate-duration-seconds)]
    (evaluate-expression* context expression)))

(defn- cql->fhir [value]
  (condp instance? value
    Integer (type/integer value)
    Long (type/integer value)
    String (type/string value)
    Date (type/date value)
    value))

(declare parameter)

(def ^:private tuple-part-xf
  (keep
   (fn [[key value]]
     (some->> value (parameter (name key))))))

(def ^:private list-part-xf
  (keep
   (fn [value]
     (some->> value (parameter "element")))))

(defn- parameter [name value]
  (let [stub {:fhir/type :fhir.Parameters/parameter
              :name (type/string name)}]
    (if (:fhir/type value)
      (if (instance? Resource value)
        (assoc stub :resource @(cr/pull value))
        (assoc stub :value value))
      (condp instance? value
        Map (assoc stub :part (into [] tuple-part-xf value))
        List (assoc stub :part (into [] list-part-xf value))
        (assoc stub :value (cql->fhir value))))))

(def ^:private parameter-xf
  (map (partial parameter "return")))

(defn- create-parameters [values]
  {:fhir/type :fhir/Parameters
   :parameter (into [] parameter-xf values)})

(defn- handler [context]
  (fn [{:keys [headers body] :blaze/keys [db] :as request}]
    (if (handler-util/preference headers "respond-async")
      (req/handle-async context request)
      (if-ok [{:keys [expression parameters]} (fu/coerce-params param-specs body)
              expression-defs (compile-library context expression (coerce-expr-params parameters))]
        (let [{:keys [expression]} (get expression-defs "Expression")]
          (-> (evaluate-expression (assoc context :db db) expression)
              (u/to-seq)
              (create-parameters)
              (ring/response)
              (ac/completed-future)))
        ac/completed-future))))

(defmethod m/pre-init-spec :blaze.fhir.operation/cql [_]
  (s/keys :req-un [:blaze.db/node :blaze/terminology-service ::executor
                   :blaze/clock :blaze/rng-fn]
          :opt [::expr/cache]
          :opt-un [::timeout]))

(defmethod ig/init-key :blaze.fhir.operation/cql [_ {:keys [timeout] :as context}]
  (log/info
   (cond-> "Init FHIR $cql operation handler"
     timeout
     (str " with a timeout of " timeout)))
  (handler context))

(defmethod m/pre-init-spec ::timeout [_]
  (s/keys :req-un [:blaze.fhir.operation.cql.timeout/millis]))

(defmethod ig/init-key ::timeout [_ {:keys [millis]}]
  (time/millis millis))

(defmethod m/pre-init-spec ::executor [_]
  (s/keys :opt-un [:blaze.fhir.operation.evaluate-measure.executor/num-threads]))

(defn- executor-init-msg [num-threads]
  (format "Init $cql operation executor with %d threads" num-threads))

(defmethod ig/init-key ::executor
  [_ {:keys [num-threads] :or {num-threads (u/available-processors)}}]
  (log/info (executor-init-msg num-threads))
  (ex/io-pool num-threads "operation-evaluate-measure-%d"))

(defmethod ig/halt-key! ::executor
  [_ executor]
  (log/info "Stopping $cql operation executor...")
  (ex/shutdown! executor)
  (if (ex/await-termination executor 10 TimeUnit/SECONDS)
    (log/info "$cql operation executor was stopped successfully")
    (log/warn "Got timeout while stopping the $cql operation executor")))

(derive ::executor :blaze.metrics/thread-pool-executor)

(reg-collector ::compile-duration-seconds
  compile-duration-seconds)

(reg-collector ::evaluate-duration-seconds
  evaluate-duration-seconds)
