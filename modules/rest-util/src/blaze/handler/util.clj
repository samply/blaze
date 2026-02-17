(ns blaze.handler.util
  "HTTP/REST Handler Utils"
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.type :as type]
   [blaze.http.util :as hu]
   [blaze.util :refer [str]]
   [clj-commons.format.exceptions :as e]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [reitit.ring]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant ZoneOffset]
   [java.util.concurrent CompletionException]))

(set! *warn-on-reflection* true)

(def ^:private valid-return-preferences
  #{"minimal" "representation" "OperationOutcome"})

(defn preference
  "Returns the value of the preference with `name` as keyword from `headers` or
  nil if there is none."
  [headers name]
  (->> (hu/parse-header-value (get headers "prefer"))
       (some
        #(when (= name (:name %))
           (if (:value %)
             (if (= "return" name)
               (when (valid-return-preferences (:value %))
                 (keyword (str "blaze.preference." name) (:value %)))
               (keyword (str "blaze.preference." name) (:value %)))
             (keyword "blaze.preference" name))))))

(defn- issue-code [category]
  (case category
    ::anom/busy #fhir/code "timeout"
    ::anom/incorrect #fhir/code "invalid"
    ::anom/not-found #fhir/code "not-found"
    ::anom/unsupported #fhir/code "not-supported"
    ::anom/conflict #fhir/code "conflict"
    #fhir/code "exception"))

(defn- operation-outcome-issues [issues category]
  (mapv (fn [{:fhir.issues/keys [severity code diagnostics expression]}]
          (cond->
           {:fhir/type :fhir.OperationOutcome/issue
            :severity #fhir/code "error"
            :code (or (some-> code type/code) (issue-code category))}
            severity
            (assoc :severity (type/code severity))
            diagnostics
            (assoc :diagnostics (type/string diagnostics))
            (coll? expression)
            (assoc :expression (mapv type/string expression))
            (and (not (coll? expression))
                 (some? expression))
            (assoc :expression [(type/string expression)])))
        issues))

(defn- operation-outcome-issue
  [{:fhir/keys [issue operation-outcome]
    :fhir.issue/keys [expression]
    :blaze/keys [stacktrace]
    ::anom/keys [category message]}]
  (cond->
   {:fhir/type :fhir.OperationOutcome/issue
    :severity #fhir/code "error"
    :code (or (some-> issue type/code) (issue-code category))}
    operation-outcome
    (assoc
     :details
     (type/codeable-concept
      {:coding
       [(type/coding
         {:system #fhir/uri-interned "http://terminology.hl7.org/CodeSystem/operation-outcome"
          :code (type/code operation-outcome)})]}))
    message
    (assoc :diagnostics (type/string message))
    stacktrace
    (assoc :diagnostics (type/string stacktrace))
    (coll? expression)
    (assoc :expression (mapv type/string expression))
    (and (not (coll? expression))
         (some? expression))
    (assoc :expression [(type/string expression)])))

(defn operation-outcome
  "Creates an FHIR OperationOutcome from an anomaly."
  {:arglists '([anomaly])}
  [{:fhir/keys [issues]
    ::anom/keys [category] :as anomaly}]
  (if issues
    {:fhir/type :fhir/OperationOutcome
     :issue (operation-outcome-issues issues category)}
    {:fhir/type :fhir/OperationOutcome
     :issue [(operation-outcome-issue anomaly)]}))

(defn- category->status [category]
  (case category
    ::anom/incorrect 400
    ::anom/forbidden 403
    ::anom/not-found 404
    ::anom/unsupported 422
    ::anom/conflict 409
    ::anom/busy 503
    500))

(defn- format-exception
  "Formats `e` without any ANSI formatting.

  Used to output stack traces in OperationOutcome's."
  [e]
  (binding [e/*fonts* nil]
    (e/format-exception e)))

(defn- headers [response {:http/keys [headers]}]
  (reduce #(apply ring/header %1 %2) response headers))

(defn- error-response* [{::anom/keys [category] :as error} f]
  (cond
    category
    (do
      (when-not (:blaze/stacktrace error)
        (if (ba/fault? category)
          (log/error error)
          (log/warn error)))
      (f error))

    (instance? CompletionException error)
    (error-response* (ex-cause error) f)

    (instance? Throwable error)
    (if (ba/anomaly? (ex-data error))
      (error-response*
       (merge
        {::anom/message (ex-message error)}
        (ex-data error))
       f)
      (do
        (log/error error)
        (error-response*
         (ba/fault
          (ex-message error)
          :blaze/stacktrace (format-exception error))
         f)))

    :else
    (error-response* {::anom/category ::anom/fault} f)))

(defn error-response
  "Converts `error` into a OperationOutcome response.

  Accepts anomalies and exceptions. Uses ::anom/category to determine the
  response status.

  Other used keys are:

  * :fhir/issue - will go into `OperationOutcome.issue.code`
  * :fhir/operation-outcome
      - will go into `OperationOutcome.issue.details` as code with system
        http://terminology.hl7.org/CodeSystem/operation-outcome
  * :fhir.issue/expression - will go into `OperationOutcome.issue.expression`
  * :http/status - the HTTP status to use
  * :http/headers - a list of tuples of header name and header value"
  [error]
  (error-response*
   error
   (fn [{::anom/keys [category] :http/keys [status] :as error}]
     (-> (ring/response (operation-outcome error))
         (headers error)
         (ring/status (or status (category->status category)))))))

(defn json-error-response
  [error]
  (error-response*
   error
   (fn [{::anom/keys [category] :http/keys [status] :as error}]
     (-> (ring/response {:msg (::anom/message error)})
         (headers error)
         (ring/status (or status (category->status category)))))))

(defn bundle-error-response
  "Returns an error response suitable for bundles.

  Accepts anomalies and exceptions."
  [error]
  (error-response*
   error
   (fn [{::anom/keys [category] :http/keys [status] :as error}]
     {:fhir/type :fhir.Bundle.entry/response
      :status (type/string (str (or status (category->status category))))
      :outcome (operation-outcome error)})))

(def ^:private not-found-issue
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "error"
   :code #fhir/code "not-found"})

(def ^:private not-found-outcome
  {:fhir/type :fhir/OperationOutcome
   :issue [not-found-issue]})

(defn not-found-handler [_]
  (ring/not-found not-found-outcome))

(defn- method-not-allowed-msg [{:keys [uri request-method]}]
  (format "Method %s not allowed on `%s` endpoint."
          (str/upper-case (name request-method)) uri))

(defn- method-not-allowed-issue [request]
  {:fhir/type :fhir.OperationOutcome/issue
   :severity #fhir/code "error"
   :code #fhir/code "processing"
   :diagnostics (type/string (method-not-allowed-msg request))})

(defn- method-not-allowed-outcome [request]
  {:fhir/type :fhir/OperationOutcome
   :issue [(method-not-allowed-issue request)]})

(defn method-not-allowed-handler [request]
  (-> (ring/response (method-not-allowed-outcome request))
      (ring/status 405)))

(def default-handler
  (reitit.ring/create-default-handler
   {:not-found not-found-handler
    :method-not-allowed method-not-allowed-handler}))

(defn method-not-allowed-batch-handler [request]
  (ac/completed-future
   (ba/forbidden
    (method-not-allowed-msg request)
    :http/status 405
    :fhir/issue "processing")))

(def default-batch-handler
  "A handler returning failed futures."
  (reitit.ring/create-default-handler
   {:method-not-allowed method-not-allowed-batch-handler}))

(defn instant [{:blaze.db.tx/keys [instant]}]
  (type/instant (.atOffset ^Instant instant ZoneOffset/UTC)))
