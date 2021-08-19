(ns blaze.handler.util
  "HTTP/REST Handler Utils"
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir.spec.type :as type]
    [blaze.http.util :as hu]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [io.aviso.exception :as aviso]
    [reitit.ring]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent CompletionException]))


(defn preference
  "Returns the value of the preference with `name` from Ring `headers`."
  [headers name]
  (->> (hu/parse-header-value (get headers "prefer"))
       (some #(when (= name (:name %)) (:value %)))))


(defn- issue-code [category]
  (case category
    ::anom/busy #fhir/code"timeout"
    ::anom/incorrect #fhir/code"invalid"
    ::anom/not-found #fhir/code"not-found"
    ::anom/unsupported #fhir/code"not-supported"
    ::anom/conflict #fhir/code"conflict"
    #fhir/code"exception"))


(defn- operation-outcome-issues [issues category]
  (mapv (fn [{:fhir.issues/keys [severity code diagnostics expression]}]
          (cond->
            {:fhir/type :fhir.OperationOutcome/issue
             :severity #fhir/code"error"
             :code (or (some-> code type/->Code) (issue-code category))}
            severity
            (assoc :severity (type/->Code severity))
            diagnostics
            (assoc :diagnostics diagnostics)
            (coll? expression)
            (assoc :expression expression)
            (and (not (coll? expression))
                 (some? expression))
            (assoc :expression [expression])))
        issues))


(defn- operation-outcome-issue
  [{:fhir/keys [issue operation-outcome]
    :fhir.issue/keys [expression]
    :blaze/keys [stacktrace]
    ::anom/keys [category message]}]
  (cond->
    {:fhir/type :fhir.OperationOutcome/issue
     :severity #fhir/code"error"
     :code (or (some-> issue type/->Code) (issue-code category))}
    operation-outcome
    (assoc
      :details
      {:coding
       [{:system #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
         :code (type/->Code operation-outcome)}]})
    message
    (assoc :diagnostics message)
    stacktrace
    (assoc :diagnostics stacktrace)
    (coll? expression)
    (assoc :expression expression)
    (and (not (coll? expression))
         (some? expression))
    (assoc :expression [expression])))


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
    ::anom/not-found 404
    ::anom/unsupported 422
    ::anom/conflict 409
    ::anom/busy 503
    500))


(defn- format-exception
  "Formats `e` without any ANSI formatting.

  Used to output stack traces in OperationOutcome's."
  [e]
  (binding [aviso/*fonts* nil]
    (aviso/format-exception e)))


(defn- headers [response {:http/keys [headers]}]
  (reduce #(apply ring/header %1 %2) response headers))


(defn error-response
  "Converts `error` into a OperationOutcome response.

  Accepts anomalies and exceptions. Uses ::anom/category to determine the
  response status.

  Other used keys are:

  * :fhir/issue - will go into `OperationOutcome.issue.code`
  * :fhir/operation-outcome
      - will go into `OperationOutcome.issue.details` as code with system
        http://terminology.hl7.org/CodeSystem/operation-outcome
  * :fhir.issue/expression - will go into `OperationOutcome.issue.expression`"
  {:arglists '([error])}
  [{::anom/keys [category] :http/keys [status] :as error}]
  (cond
    category
    (do
      (when-not (:blaze/stacktrace error)
        (case category
          ::anom/fault
          (log/error error)
          (log/warn error)))
      (-> (ring/response (operation-outcome error))
          (headers error)
          (ring/status (or status (category->status category)))))

    (instance? CompletionException error)
    (error-response (ex-cause error))

    (instance? Throwable error)
    (if (::anom/category (ex-data error))
      (error-response
        (merge
          {::anom/message (ex-message error)}
          (ex-data error)))
      (do
        (log/error (log/stacktrace error))
        (error-response
          {::anom/category ::anom/fault
           ::anom/message (ex-message error)
           :blaze/stacktrace (format-exception error)})))

    :else
    (error-response {::anom/category ::anom/fault})))


(defn bundle-error-response
  "Returns an error response suitable for bundles.

  Accepts anomalies and exceptions."
  {:arglists '([error])}
  [{::anom/keys [category] :as error}]
  (cond
    category
    (do
      (when-not (:blaze/stacktrace error)
        (case category
          ::anom/fault
          (log/error error)
          (log/warn error)))
      {:fhir/type :fhir.Bundle.entry/response
       :status (str (category->status category))
       :outcome (operation-outcome error)})

    (instance? Throwable error)
    (if (::anom/category (ex-data error))
      (bundle-error-response
        (merge
          {::anom/message (ex-message error)}
          (ex-data error)))
      (do
        (log/error (log/stacktrace error))
        (bundle-error-response
          {::anom/category ::anom/fault
           ::anom/message (ex-message error)
           :blaze/stacktrace (format-exception error)})))

    :else
    (bundle-error-response {::anom/category ::anom/fault})))


(def ^:private not-found-issue
  {:severity #fhir/code"error"
   :code #fhir/code"not-found"})


(def ^:private not-found-outcome
  {:fhir/type :fhir/OperationOutcome
   :issue [not-found-issue]})


(defn- not-found-handler [_]
  (-> (ring/not-found not-found-outcome) ac/completed-future))


(defn- method-not-allowed-msg [{:keys [uri request-method]}]
  (format "Method %s not allowed on `%s` endpoint."
          (str/upper-case (name request-method)) uri))


(defn- method-not-allowed-issue [request]
  {:severity #fhir/code"error"
   :code #fhir/code"processing"
   :diagnostics (method-not-allowed-msg request)})


(defn- method-not-allowed-outcome [request]
  {:fhir/type :fhir/OperationOutcome
   :issue [(method-not-allowed-issue request)]})


(defn- method-not-allowed-handler [request]
  (-> (ring/response (method-not-allowed-outcome request))
      (ring/status 405)
      ac/completed-future))


(def ^:private not-acceptable-issue
  {:severity #fhir/code"error"
   :code #fhir/code"structure"})


(def ^:private not-acceptable-outcome
  {:fhir/type :fhir/OperationOutcome
   :issue [not-acceptable-issue]})


(defn- not-acceptable-handler [_]
  (-> (ring/response not-acceptable-outcome)
      (ring/status 406)
      ac/completed-future))


(def default-handler
  (reitit.ring/create-default-handler
    {:not-found not-found-handler
     :method-not-allowed method-not-allowed-handler
     :not-acceptable not-acceptable-handler}))
