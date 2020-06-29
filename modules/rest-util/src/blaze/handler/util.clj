(ns blaze.handler.util
  "HTTP/REST Handler Utils"
  (:require
    [blaze.async-comp :as ac]
    [blaze.db.api :as d]
    [clojure.core.protocols :refer [Datafiable]]
    [clojure.datafy :refer [datafy]]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [io.aviso.exception :as aviso]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [org.apache.http HeaderElement]
    [org.apache.http.message BasicHeaderValueParser]
    [java.util.concurrent CompletionException]))


(set! *warn-on-reflection* true)


(extend-protocol Datafiable
  HeaderElement
  (datafy [element]
    {:name (str/lower-case (.getName element))
     :value (.getValue element)}))


(defn parse-header-value
  "Parses the header value string `s` into elements which have a :name and a
  :value.

  The element name is converted to lower-case."
  [s]
  (when s
    (->> (BasicHeaderValueParser/parseElements
           s BasicHeaderValueParser/INSTANCE)
         (into [] (map datafy)))))


(defn preference
  "Returns the value of the preference with `name` from Ring `headers`."
  [headers name]
  (->> (parse-header-value (get headers "prefer"))
       (some #(when (= name (:name %)) (:value %)))))


(defn- issue-code [category]
  (case category
    ::anom/busy "timeout"
    ::anom/incorrect "invalid"
    ::anom/not-found "not-found"
    ::anom/unsupported "not-supported"
    ::anom/conflict "conflict"
    "exception"))


(defn operation-outcome
  [{:fhir/keys [issue operation-outcome]
    :fhir.issue/keys [expression]
    :blaze/keys [stacktrace]
    ::anom/keys [category message]}]
  {:resourceType "OperationOutcome"
   :issue
   [(cond->
      {:severity "error"
       :code (or issue (issue-code category))}
      operation-outcome
      (assoc
        :details
        {:coding
         [{:system "http://terminology.hl7.org/CodeSystem/operation-outcome"
           :code operation-outcome}]})
      message
      (assoc :diagnostics message)
      stacktrace
      (assoc :diagnostics stacktrace)
      (coll? expression)
      (assoc :expression expression)
      (and (not (coll? expression))
           (some? expression))
      (assoc :expression [expression]))]})


(defn- category->status [category]
  (case category
    ::anom/incorrect 400
    ::anom/not-found 404
    ::anom/unsupported 422
    ::anom/conflict 409
    ::anom/busy 503
    500))


(defn error-response
  "Converts `error` into a OperationOutcome response. Uses ::anom/category to
  determine the response status.

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
           :blaze/stacktrace (aviso/format-exception error)})))

    :else
    (error-response {::anom/category ::anom/fault})))


(defn bundle-error-response
  "Returns an error response suitable for bundles. Accepts anomalies and
  exceptions."
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
      {:status (str (category->status category))
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
           :blaze/stacktrace (aviso/format-exception error)})))

    :else
    (bundle-error-response {::anom/category ::anom/fault})))


(defn db
  "Returns a CompletableFuture that will complete with the value of the
  database, optionally as of some point in time `t`."
  [node t]
  (if t
    (-> (d/sync node t) (ac/then-apply #(d/as-of % t)))
    (ac/completed-future (d/db node))))
