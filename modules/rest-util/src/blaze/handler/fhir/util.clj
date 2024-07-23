(ns blaze.handler.fhir.util
  "Utilities for FHIR interactions."
  (:refer-clojure :exclude [sync])
  (:require
   [blaze.anomaly :as ba :refer [if-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.impl.index.resource-handle :as rh]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.handler.util :as handler-util]
   [blaze.util :as u]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [reitit.core :as reitit])
  (:import
   [java.time Instant ZoneId ZonedDateTime]
   [java.time.format DateTimeFormatter]
   [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(defn parse-nat-long [s]
  (when-let [n (parse-long s)]
    (when-not (neg? n)
      n)))

(defn t
  "Returns the t (optional) of the database which should be stay stable.

  Tries to read the t from the query param `__t` and returns the first valid one
  if there is any."
  {:arglists '([query-params])}
  [{v "__t"}]
  (some parse-nat-long (u/to-seq v)))

(def ^:private ^:const default-page-size 50)
(def ^:private ^:const max-page-size 10000)

(defn page-size
  "Returns the page size taken from a possible `_count` query param.

  Returns the value from the first valid `_count` query param or `default`
  (defaults to 50). Limits value at `max` (defaults to 10000)."
  {:arglists
   '([query-params]
     [query-params max default])}
  ([query-params]
   (page-size query-params max-page-size default-page-size))
  ([{v "_count"} max default]
   (or (some #(some-> (parse-nat-long %) (min max)) (u/to-seq v)) default)))

(defn page-offset
  "Returns the page offset taken from a possible `__page-offset` query param.

  Returns the value from the first valid `__page-offset` query param or the
  default value of 0."
  {:arglists '([query-params])}
  [{v "__page-offset"}]
  (or (some parse-nat-long (u/to-seq v)) 0))

(defn page-type
  "Returns the value of the first valid `__page-type` query param or nil
  otherwise.

  Values have to be valid FHIR resource type names."
  {:arglists '([query-params])}
  [{v "__page-type"}]
  (some #(when (s/valid? :fhir.resource/type %) %) (u/to-seq v)))

(defn page-id
  "Returns the value of the first valid `__page-id` query param or nil
  otherwise.

  Values have to be valid FHIR id's."
  {:arglists '([query-params])}
  [{v "__page-id"}]
  (some #(when (s/valid? :blaze.resource/id %) %) (u/to-seq v)))

(defn elements
  "Returns a vector of keywords created from the comma separated values of 
   all `_elements` query params."
  {:arglists '([query-params])}
  [{v "_elements"}]
  (into [] (comp (mapcat #(str/split % #"\s*,\s*")) (remove str/blank?) (map keyword)) (u/to-seq v)))

(defn- incorrect-date-msg [name value]
  (format "The value `%s` of the query param `%s` is no valid date." value name))

(defn date
  "Returns the value of the query param with `name` parsed as FHIR date or nil
  if not found.

  Returns an anomaly if the query param is available but can't be converted to a
  FHIR date."
  {:arglists '([query-params name])}
  [query-params name]
  (when-let [value (get query-params name)]
    (let [date (system/parse-date value)]
      (if (ba/anomaly? date)
        (ba/incorrect (incorrect-date-msg name value))
        date))))

(defn type-url
  "Returns the URL of a resource type like `[base]/[type]`."
  [{:blaze/keys [base-url] ::reitit/keys [router]} type]
  (let [{:keys [path]} (reitit/match-by-name router (keyword type "type"))]
    (str base-url path)))

(defn instance-url
  "Returns the URL of an instance (resource) like `[base]/[type]/[id]`."
  [context type id]
  ;; URLs are build by hand here, because id's do not need to be URL encoded
  ;; and the URL encoding in reitit is slow: https://github.com/metosin/reitit/issues/477
  (str (type-url context type) "/" id))

(defn versioned-instance-url
  "Returns the URL of a versioned instance (resource) like
  `[base]/[type]/[id]/_history/[vid]`."
  [context type id vid]
  ;; URLs are build by hand here, because id's do not need to be URL encoded
  ;; and the URL encoding in reitit is slow: https://github.com/metosin/reitit/issues/477
  (str (instance-url context type id) "/_history/" vid))

(def ^:private gmt (ZoneId/of "GMT"))

(defn last-modified
  "Returns the instant of `tx` formatted suitable for the Last-Modified HTTP
  header."
  {:arglists '([tx])}
  [{:blaze.db.tx/keys [instant]}]
  (->> (ZonedDateTime/ofInstant instant gmt)
       (.format DateTimeFormatter/RFC_1123_DATE_TIME)))

(defn etag
  "Returns the t of `tx` formatted as ETag."
  {:arglists '([tx])}
  [{:blaze.db/keys [t]}]
  (str "W/\"" t "\""))

(defn- resource-handle [db type id]
  (if-let [handle (d/resource-handle db type id)]
    (if (rh/deleted? handle)
      (let [tx (d/tx db (rh/t handle))]
        (ba/not-found
         (format "Resource `%s/%s` was deleted." type id)
         :http/status 410
         :http/headers
         [["Last-Modified" (last-modified tx)]
          ["ETag" (etag tx)]]
         :fhir/issue "deleted"))
      handle)
    (ba/not-found
     (format "Resource `%s/%s` was not found." type id)
     :fhir/issue "not-found")))

(defn pull
  "Returns a CompletableFuture that will complete with the resource with `type`
  and `id` if not deleted in `db` or an anomaly otherwise.

  Returns a not-found anomaly if the resource was not found or is deleted. In
  case it is deleted, sets :http/status to 410 and :http/headers Last-Modified
  and ETag to appropriate values.

  Functions applied after the returned future are executed on the common
  ForkJoinPool."
  [db type id]
  (if-ok [resource-handle (resource-handle db type id)]
    (-> (d/pull db resource-handle)
        (ac/exceptionally
         #(assoc %
                 ::anom/category ::anom/fault
                 :fhir/issue "incomplete")))
    ac/completed-future))

(defn- timeout-msg [timeout]
  (format "Timeout while trying to acquire the latest known database state. At least one known transaction hasn't been completed yet. Please try to lower the transaction load or increase the timeout of %d ms by setting DB_SYNC_TIMEOUT to a higher value if you see this often." timeout))

(defn- timeout-t-msg [t timeout]
  (format "Timeout while trying to acquire the database state with t=%d. The indexer has probably fallen behind. Please try to lower the transaction load or increase the timeout of %d ms by setting DB_SYNC_TIMEOUT to a higher value if you see this often." t timeout))

(defn sync
  "Like `blaze.db.api/sync` but fails after `timeout` milliseconds."
  ([node timeout]
   (-> (d/sync node)
       (ac/or-timeout! timeout TimeUnit/MILLISECONDS)
       (ac/exceptionally #(cond-> % (ba/busy? %) (assoc ::anom/message (timeout-msg timeout))))))
  ([node t timeout]
   (-> (do-sync [db (d/sync node t)]
         (d/as-of db t))
       (ac/or-timeout! timeout TimeUnit/MILLISECONDS)
       (ac/exceptionally #(cond-> % (ba/busy? %) (assoc ::anom/message (timeout-t-msg t timeout)))))))

(defn- batch-request
  {:arglists '([context bundle-entry])}
  [{:keys [context-path]
    :blaze/keys [base-url db cancelled?]
    return-preference :blaze.preference/return
    :or {context-path ""}}
   {{:keys [method url]
     if-none-match :ifNoneMatch
     if-match :ifMatch
     if-none-exist :ifNoneExist}
    :request :keys [resource]}]
  (let [url (-> url type/value u/strip-leading-slashes)
        [url query-string] (str/split url #"\?")
        method (keyword (str/lower-case (type/value method)))
        return-preference (or return-preference
                              (when (#{:post :put} method)
                                :blaze.preference.return/minimal))]
    (cond->
     {:uri (str context-path "/" url)
      :request-method method
      :blaze/base-url base-url}

      query-string
      (assoc :query-string query-string)

      return-preference
      (assoc-in [:headers "prefer"] (str "return=" (name return-preference)))

      if-none-match
      (assoc-in [:headers "if-none-match"] if-none-match)

      if-match
      (assoc-in [:headers "if-match"] if-match)

      if-none-exist
      (assoc-in [:headers "if-none-exist"] if-none-exist)

      resource
      (assoc :body resource)

      db
      (assoc :blaze/db db)

      cancelled?
      (assoc :blaze/cancelled? cancelled?))))

(defn- convert-http-date
  "Converts string `s` representing an HTTP date into a FHIR instant."
  [s]
  (Instant/from (.parse DateTimeFormatter/RFC_1123_DATE_TIME s)))

(defn- bundle-response
  {:arglists '([ring-response])}
  [{:keys [status body]
    {etag "ETag"
     last-modified "Last-Modified"
     location "Location"}
    :headers}]
  (cond->
   {:fhir/type :fhir.Bundle/entry
    :response
    (cond->
     {:fhir/type :fhir.Bundle.entry/response
      :status (str status)}

      location
      (assoc :location location)

      etag
      (assoc :etag etag)

      last-modified
      (assoc :lastModified (convert-http-date last-modified)))}

    body
    (assoc :resource body)))

(defn- response-entry [response]
  {:fhir/type :fhir.Bundle/entry :response response})

(defn- with-entry-location* [issues idx]
  (mapv #(assoc % :expression [(format "Bundle.entry[%d]" idx)]) issues))

(defn- with-entry-location [outcome idx]
  (update outcome :issue with-entry-location* idx))

(defn- bundle-error-response [idx]
  (comp
   response-entry
   (fn [error]
     (-> (handler-util/bundle-error-response error)
         (update :outcome with-entry-location idx)))))

(def ^:private type-part "[A-Z](?:[A-Za-z0-9_]){0,254}")
(def ^:private id-part "[A-Za-z0-9-.]{1,64}")

(def ^:private type-pattern
  (re-pattern (format "(%s)" type-part)))

(def ^:private type-id-pattern
  (re-pattern (format "(%s)/(%s)" type-part id-part)))

(def ^:private type-operation-pattern
  (re-pattern (format "(%s)/[^/]+" type-part)))

(def ^:private type-id-operation-pattern
  (re-pattern (format "(%s)/(%s)/[^/]+" type-part id-part)))

(defn match-url
  "Tries to parse a `type` and `id` from `url`. Returns a map with :type and :id
  if successful. Otherwise nil.

  If `url` contains an operation, adds :kind :operation to the map."
  [url]
  (or (when-let [[_ type] (re-matches type-pattern url)]
        {:type type})
      (when-let [[_ type id] (re-matches type-id-pattern url)]
        {:type type :id id})
      (when-let [[_ type] (re-matches type-operation-pattern url)]
        {:type type :kind :operation})
      (when-let [[_ type id] (re-matches type-id-operation-pattern url)]
        {:type type :id id :kind :operation})))

(defn- missing-request-anom [idx]
  (ba/incorrect
   "Missing request."
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d]" idx)))

(defn- missing-request-url-anom [idx]
  (ba/incorrect
   "Missing request URL."
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request" idx)))

(defn- missing-request-method-anom [idx]
  (ba/incorrect
   "Missing request method."
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request" idx)))

(defn- unknown-request-method-anom [method idx]
  (ba/incorrect
   (format "Unknown request method `%s`." method)
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request.method" idx)))

(defn- unsupported-request-method-anom [method idx]
  (ba/unsupported
   (format "Unsupported request method `%s`." method)
   :fhir/issue "not-supported"
   :fhir.issue/expression (format "Bundle.entry[%d].request.method" idx)))

(defn- missing-request-url-type-anom [url idx]
  (ba/incorrect
   (format "Can't parse type from request URL `%s`." url)
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)))

(defn- unknown-request-url-type-anom [type url idx]
  (ba/incorrect
   (format "Unknown type `%s` in bundle entry request URL `%s`." type url)
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)))

(defn- missing-resource-type-anom [idx]
  (ba/incorrect
   "Missing resource type."
   :fhir/issue "required"
   :fhir.issue/expression (format "Bundle.entry[%d].resource" idx)))

(defn- type-mismatch-anom [resource url idx]
  (ba/incorrect
   (format "Type mismatch between resource type `%s` and URL `%s`."
           (-> resource :fhir/type name) url)
   :fhir/issue "invariant"
   :fhir.issue/expression
   [(format "Bundle.entry[%d].request.url" idx)
    (format "Bundle.entry[%d].resource.resourceType" idx)]
   :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"))

(defn- missing-request-url-id-anom [url idx]
  (ba/incorrect
   (format "Can't parse id from URL `%s`." url)
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)))

(defn- missing-resource-id-anom [idx]
  (ba/incorrect
   "Resource id is missing."
   :fhir/issue "required"
   :fhir.issue/expression (format "Bundle.entry[%d].resource.id" idx)
   :fhir/operation-outcome "MSG_RESOURCE_ID_MISSING"))

(defn- subsetted-anom [idx]
  (ba/incorrect
   "Resources with tag SUBSETTED may be incomplete and so can't be used in updates."
   :fhir/issue "processing"
   :fhir.issue/expression (format "Bundle.entry[%d].resource" idx)))

(defn- id-mismatch-anom [resource url idx]
  (ba/incorrect
   (format "Id mismatch between resource id `%s` and URL `%s`."
           (:id resource) url)
   :fhir/issue "invariant"
   :fhir.issue/expression
   [(format "Bundle.entry[%d].request.url" idx)
    (format "Bundle.entry[%d].resource.id" idx)]
   :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"))

(defn subsetted?
  "Checks whether `coding` is a SUBSETTED coding."
  {:arglists '([coding])}
  [{:keys [system code]}]
  (and (= #fhir/uri"http://terminology.hl7.org/CodeSystem/v3-ObservationValue" system)
       (= #fhir/code"SUBSETTED" code)))

(defn validate-entry
  "Validates that bundle `entry` can be used in a transaction or batch.

  Returns `entry` unmodified or an anomaly in case it isn't valid. Uses `idx` in
  anomalies to indicate the position of the entry in the bundle."
  {:arglists '([idx entry])}
  [idx {:keys [request resource] :as entry}]
  (let [method (some-> request :method type/value)
        [url] (some-> request :url type/value u/strip-leading-slashes (str/split #"\?"))
        {:keys [type id kind]} (some-> url match-url)]
    (cond
      (nil? request)
      (missing-request-anom idx)

      (nil? url)
      (missing-request-url-anom idx)

      (nil? method)
      (missing-request-method-anom idx)

      (not (#{"GET" "HEAD" "POST" "PUT" "DELETE" "PATCH"} method))
      (unknown-request-method-anom method idx)

      (not (#{"GET" "POST" "PUT" "DELETE"} method))
      (unsupported-request-method-anom method idx)

      (and (= "GET" method) (= "metadata" url))
      entry

      (nil? type)
      (missing-request-url-type-anom url idx)

      (not (fhir-spec/type-exists? type))
      (unknown-request-url-type-anom type url idx)

      (and (#{"POST" "PUT"} method) (nil? (:fhir/type resource)))
      (missing-resource-type-anom idx)

      (and (#{"POST" "PUT"} method) (not= :operation kind) (not= type (-> resource :fhir/type name)))
      (type-mismatch-anom resource url idx)

      (and (= "POST" method) (= :operation kind) (not= "Parameters" (-> resource :fhir/type name)))
      (type-mismatch-anom resource url idx)

      (and (#{"POST" "PUT"} method) (->> resource :meta :tag (some subsetted?)))
      (subsetted-anom idx)

      (and (= "PUT" method) (nil? id))
      (missing-request-url-id-anom url idx)

      (and (= "PUT" method) (not (contains? resource :id)))
      (missing-resource-id-anom idx)

      (and (= "PUT" method) (not= id (:id resource)))
      (id-mismatch-anom resource url idx)

      :else
      entry)))

(defn process-batch-entry
  "Processes `entry` from `idx` of a batch bundle using :batch-handler from
  `context`."
  {:arglists '([context idx entry])}
  [{:keys [batch-handler] :as context} idx entry]
  (if-ok [_ (validate-entry idx entry)]
    (-> (batch-handler (batch-request context entry))
        (ac/then-apply bundle-response)
        (ac/exceptionally
         (fn [anom]
           (if (ba/interrupted? anom)
             anom
             ((bundle-error-response idx) anom)))))
    (comp ac/completed-future response-entry
          handler-util/bundle-error-response)))

(defn process-batch-entries
  "Processes `entries` of a batch bundle using :batch-handler from `context`."
  [context entries]
  (let [futures (map-indexed (partial process-batch-entry context) entries)]
    (do-sync [_ (ac/all-of futures)]
      (mapv ac/join futures))))
