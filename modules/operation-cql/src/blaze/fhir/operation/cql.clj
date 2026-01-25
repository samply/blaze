(ns blaze.fhir.operation.cql
  "Main entry point into the $cql operation."
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac]
   [blaze.cql.translator :as cql-translator]
   [blaze.db.api :as d]
   [blaze.elm.compiler.library :as library]
   [blaze.elm.expression :as expr]
   [blaze.elm.resource :as cr]
   [blaze.fhir.operation.cql.spec]
   [blaze.fhir.spec.references :as fsr]
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
   [java.util List Map]))

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

(defn- coerce-subject [value]
  (if (identical? :fhir/string (:fhir/type value))
    (when-some [value (:value value)]
      (if-some [[type id] (fsr/split-literal-ref value)]
        (if (= "Patient" type)
          [type id]
          (ba/unsupported (format "Unsupported subject type `%s`." type)))
        (ba/incorrect (format "Invalid subject `%s` expect `<type>/<id>`." value))))
    (ba/incorrect "Expect FHIR string.")))

(def ^:private param-specs
  {"subject" {:action :copy :coerce coerce-subject}
   "expression" {:action :copy}
   "parameters" {:action :copy-resource}
   "data" {}
   "dataEndpoint" {}
   "contentEndpoint" {}
   "terminologyEndpoint" {}})

(defn- fhir->cql [value]
  (case (:fhir/type value)
    :fhir/integer {:type "System.Integer" :value (:value value)}
    :fhir/string {:type "System.String" :value (:value value)}
    (ba/unsupported (format "Unsupported CQL type mapping from FHIR type `%s`." (name (:fhir/type value))))))

(defn- coerce-expr-params [{params :parameter}]
  (reduce
   (fn [res {{name :value} :name value :value}]
     (if-ok [value (fhir->cql value)]
       (if (res name)
         (reduced (ba/unsupported (format "Unsupported multiple param `%s`." name)))
         (assoc res name value))
       reduced))
   {}
   params))

(def ^:private cql-library-template
  "library CQL
   using FHIR version '4.0.0'
   include FHIRHelpers version '4.0.0'

   %s

   context %s

   define Expression: %s")

(defn- cql-parameter [[name {:keys [type]}]]
  (format "parameter %s %s" name type))

(defn- cql-parameters [parameters]
  (str/join "\n" (map cql-parameter parameters)))

(defn- create-cql-library
  "Creates the CQL Library as string."
  [{:keys [subject expression parameters]}]
  (format
   cql-library-template
   (cql-parameters parameters)
   (if subject "Patient" "Unfiltered")
   expression))

(defn- translate [cql-code]
  (-> (cql-translator/translate cql-code)
      (ba/exceptionally #(assoc % :fhir/issue "value"))))

(defn- compile-library** [context elm-library]
  (-> (library/compile-library context elm-library {})
      (ba/exceptionally #(assoc % :http/status 400 :fhir/issue "value"))))

(defn- extract-param-value [parameters]
  (into {} (map (fn [[name {:keys [value]}]] [name value])) parameters))

(defn- compile-library* [context {:keys [parameters] :as params}]
  (when-ok [elm-library (translate (create-cql-library params))
            {:keys [expression-defs]} (compile-library** context elm-library)]
    (cond-> expression-defs
      (seq parameters) (library/resolve-params (extract-param-value parameters)))))

(defn- compile-library [context db params]
  (with-open [_ (prom/timer compile-duration-seconds)
              db (d/new-batch-db db)]
    (when-ok [expression-defs (compile-library* context params)]
      (library/optimize db expression-defs))))

(defn- evaluate-expression-error-msg [e]
  (format "Error while evaluating the expression: %s" (ex-message e)))

(defn- evaluate-expression** [context expression resource]
  (try
    (expr/eval context expression resource)
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

(defn- evaluate-expression* [context expression resource]
  (when-ok [result (evaluate-expression** context expression resource)]
    (u/to-seq result)))

(defn- evaluate-expression [context db expression resource]
  (with-open [_ (prom/timer evaluate-duration-seconds)
              db (d/new-batch-db db)]
    (evaluate-expression* (assoc context :db db) expression resource)))

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

(defn- eval-context [{:keys [clock] :as context}]
  (assoc context :now (time/offset-date-time clock)))

(defn- missing-subject-msg [type id]
  (format "Subject with type `%s` and id `%s` was not found." type id))

(defn- subject-handle [db type id]
  (if-let [handle (d/resource-handle db type id)]
    (if (d/deleted? handle)
      (ba/incorrect (missing-subject-msg type id))
      handle)
    (ba/incorrect (missing-subject-msg type id))))

(defn- subject-resource [db {[type id :as subject] :subject}]
  (when subject
    (when-ok [subject-handle (subject-handle db type id)]
      (cr/mk-resource db subject-handle))))

(defn- handler [context]
  (fn [{:keys [headers body] :blaze/keys [db] :as request}]
    (if (handler-util/preference headers "respond-async")
      (req/handle-async context request)
      (if-ok [params (fu/coerce-params param-specs body)
              params (ba/update params :parameters coerce-expr-params)
              subject-resource (subject-resource db params)
              expression-defs (compile-library context db params)]
        (let [{:keys [expression]} (get expression-defs "Expression")]
          (if-ok [values (evaluate-expression (eval-context context) db
                                              expression subject-resource)]
            (-> (create-parameters values)
                (ring/response)
                (ac/completed-future))
            ac/completed-future))
        ac/completed-future))))

(defmethod m/pre-init-spec :blaze.fhir.operation/cql [_]
  (s/keys :req-un [:blaze.db/node :blaze/terminology-service :blaze/clock
                   :blaze/rng-fn]
          :opt [::expr/cache]))

(defmethod ig/init-key :blaze.fhir.operation/cql [_ context]
  (log/info "Init FHIR $cql operation handler")
  (handler context))

(reg-collector ::compile-duration-seconds
  compile-duration-seconds)

(reg-collector ::evaluate-duration-seconds
  evaluate-duration-seconds)
