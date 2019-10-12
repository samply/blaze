(ns blaze.handler.util
  "HTTP/REST Handler Utils"
  (:require
    [clojure.core.protocols :refer [Datafiable]]
    [clojure.datafy :refer [datafy]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [io.aviso.exception :as aviso]
    [manifold.deferred :as md :refer [deferred?]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [org.apache.http HeaderElement]
    [org.apache.http.message BasicHeaderValueParser]))


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


(s/fdef preference
  :args (s/cat :headers (s/nilable map?) :name string?)
  :ret (s/nilable string?))

(defn preference
  "Returns the value of the preference with `name` from Ring `headers`."
  [headers name]
  (->> (parse-header-value (get headers "prefer"))
       (some #(when (= name (:name %)) (:value %)))))


(defn operation-outcome
  [{:fhir/keys [issue operation-outcome]
    :fhir.issue/keys [expression]
    :blaze/keys [stacktrace]
    ::anom/keys [category message]}]
  {:resourceType "OperationOutcome"
   :issue
   [(cond->
      {:severity "error"
       :code (or issue (when (= ::anom/busy category) "timeout") "exception")}
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


(defn- status [category]
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
  [{::anom/keys [category] :as error}]
  (cond
    category
    (do
      (when-not (:blaze/stacktrace error)
        (case category
          ::anom/fault
          (log/error error)
          (log/warn error)))
      (-> (ring/response (operation-outcome error))
          (ring/status (status category))))

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
      {:status (str (status category))
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


(s/fdef db
  :args (s/cat :conn ::ds/conn :t (s/nilable nat-int?))
  :ret (s/or :deferred deferred? :db ::ds/db))

(defn db
  "Retrieves a value of the database, optionally as of some point `t`.

  When `t` is non-nil, returns a deferred which will be realized when the
  database with `t` is available."
  [conn t]
  (if t
    (-> (d/sync conn t) (md/chain #(d/as-of % t)))
    (d/db conn)))
