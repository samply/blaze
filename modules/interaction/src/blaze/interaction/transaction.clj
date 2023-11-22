(ns blaze.interaction.transaction
  "FHIR batch/transaction interaction.

  https://www.hl7.org/fhir/http.html#transaction"
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.transaction.bundle :as bundle]
   [blaze.interaction.transaction.bundle.url :as url]
   [blaze.interaction.util :as iu]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [reitit.ring]
   [ring.util.codec :as ring-codec]
   [ring.util.response :as ring]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]
   [java.time.format DateTimeFormatter]))

(set! *warn-on-reflection* true)

(defn- strip-leading-slash [s]
  (if (str/starts-with? s "/") (subs s 1) s))

(defn- missing-request-anom [idx]
  (ba/incorrect
   "Missing request."
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d]" idx)))

(defn- missing-url-anom [idx]
  (ba/incorrect
   "Missing url."
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request" idx)))

(defn- missing-method-anom [idx]
  (ba/incorrect
   "Missing method."
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request" idx)))

(defn- unknown-method-anom [method idx]
  (ba/incorrect
   (format "Unknown method `%s`." method)
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request.method" idx)))

(defn- unsupported-method-anom [method idx]
  (ba/unsupported
   (format "Unsupported method `%s`." method)
   :fhir/issue "not-supported"
   :fhir.issue/expression (format "Bundle.entry[%d].request.method" idx)))

(defn- missing-type-anom [url idx]
  (ba/incorrect
   (format "Can't parse type from `entry.request.url` `%s`." url)
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)))

(defn- unknown-type-anom [type url idx]
  (ba/incorrect
   (format "Unknown type `%s` in bundle entry URL `%s`." type url)
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)))

(defn- missing-resource-type-anom [idx]
  (ba/incorrect
   "Resource type is missing."
   :fhir/issue "required"
   :fhir.issue/expression (format "Bundle.entry[%d].resource.resourceType" idx)))

(defn type-mismatch-anom [resource url idx]
  (ba/incorrect
   (format "Type mismatch between resource type `%s` and URL `%s`."
           (-> resource :fhir/type name) url)
   :fhir/issue "invariant"
   :fhir.issue/expression
   [(format "Bundle.entry[%d].request.url" idx)
    (format "Bundle.entry[%d].resource.resourceType" idx)]
   :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"))

(defn- missing-url-id-anom [url idx]
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

(defn- invalid-resource-id-anom [resource idx]
  (ba/incorrect
   (format "Resource id `%s` is invalid." (:id resource))
   :fhir/issue "value"
   :fhir.issue/expression (format "Bundle.entry[%d].resource.id" idx)
   :fhir/operation-outcome "MSG_ID_INVALID"))

(defn- id-mismatch-anom [resource url idx]
  (ba/incorrect
   (format "Id mismatch between resource id `%s` and URL `%s`."
           (:id resource) url)
   :fhir/issue "invariant"
   :fhir.issue/expression
   [(format "Bundle.entry[%d].request.url" idx)
    (format "Bundle.entry[%d].resource.id" idx)]
   :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"))

(defn- validate-entry [idx {:keys [request resource] :as entry}]
  (let [method (some-> request :method type/value)
        [url] (some-> request :url type/value strip-leading-slash (str/split #"\?"))
        [type id] (some-> url url/match-url)]
    (cond
      (nil? request)
      (missing-request-anom idx)

      (nil? url)
      (missing-url-anom idx)

      (nil? method)
      (missing-method-anom idx)

      (not (#{"GET" "HEAD" "POST" "PUT" "DELETE" "PATCH"} method))
      (unknown-method-anom method idx)

      (not (#{"GET" "POST" "PUT" "DELETE"} method))
      (unsupported-method-anom method idx)

      (and (= "GET" method) (= "metadata" url))
      entry

      (nil? type)
      (missing-type-anom url idx)

      (not (fhir-spec/type-exists? type))
      (unknown-type-anom type url idx)

      (and (#{"POST" "PUT"} method) (nil? (:fhir/type resource)))
      (missing-resource-type-anom idx)

      (and (#{"POST" "PUT"} method) (not= type (-> resource :fhir/type name)))
      (type-mismatch-anom resource url idx)

      (and (#{"POST" "PUT"} method) (->> resource :meta :tag (some iu/subsetted?)))
      (subsetted-anom idx)

      (and (= "PUT" method) (nil? id))
      (missing-url-id-anom url idx)

      (and (= "PUT" method) (not (contains? resource :id)))
      (missing-resource-id-anom idx)

      (and (= "PUT" method) (not (s/valid? :blaze.resource/id (:id resource))))
      (invalid-resource-id-anom resource idx)

      (and (= "PUT" method) (not= id (:id resource)))
      (id-mismatch-anom resource url idx)

      :else
      entry)))

(def ^:private validate-entry-xf
  (comp (map-indexed validate-entry)
        (halt-when ba/anomaly?)))

(defn- validate-entries [entries]
  (transduce validate-entry-xf conj [] entries))

(defn- prepare-entry [res {{:keys [method]} :request :as entry}]
  (case (type/value method)
    "POST"
    (let [entry (update entry :resource assoc :id (first (:luids res)))]
      (-> (update res :luids next)
          (update :entries conj entry)))

    (update res :entries conj entry)))

(defn- validate-and-prepare-bundle
  "Validates the bundle and returns its entries.

  Returns an anomaly in case of errors."
  [context {resource-type :fhir/type :keys [type] entries :entry :as bundle}]
  (let [type (type/value type)]
    (cond
      (nil? bundle)
      (ba/incorrect
       "Missing Bundle."
       :fhir/issue "invalid")

      (not= :fhir/Bundle resource-type)
      (ba/incorrect
       (format "Expected a Bundle resource but got a %s resource."
               (name resource-type))
       :fhir/issue "value")

      (not (#{"batch" "transaction"} type))
      (ba/incorrect
       (format "Expected a Bundle type of batch or transaction but was `%s`." type)
       :fhir/issue "value")

      :else
      (if (= "transaction" type)
        (when-ok [entries (validate-entries entries)]
          (-> (reduce
               prepare-entry
               {:luids (iu/successive-luids context) :entries []}
               entries)
              :entries))
        entries))))

(defmulti build-response-entry
  "Builds the response entry of `request-entry` using the `db` after the
  transaction."
  {:arglists '([context idx entry])}
  (fn [_ _ {{:keys [method]} :request}] (type/value method)))

(defn- location [context type id vid]
  (fhir-util/versioned-instance-url context type id vid))

(defn- created-entry
  [{:keys [db] :as context} type {:keys [id] :as handle}]
  (let [tx (d/tx db (:t handle))
        vid (str (:blaze.db/t tx))]
    {:fhir/type :fhir.Bundle/entry
     :response
     {:fhir/type :fhir.Bundle.entry/response
      :status "201"
      :location (location context type id vid)
      :etag (str "W/\"" vid "\"")
      :lastModified (:blaze.db.tx/instant tx)}}))

(defn- noop-entry [db handle]
  (let [tx (d/tx db (:t handle))
        vid (str (:blaze.db/t tx))]
    {:fhir/type :fhir.Bundle/entry
     :response
     {:fhir/type :fhir.Bundle.entry/response
      :status "200"
      :etag (str "W/\"" vid "\"")
      :lastModified (:blaze.db.tx/instant tx)}}))

(defn- conditional-clauses [if-none-exist]
  (when-not (str/blank? if-none-exist)
    (-> if-none-exist ring-codec/form-decode iu/search-clauses)))

(defn- resource-content-not-found-msg [{:blaze.db/keys [resource-handle]}]
  (format "The transaction was successful but the resource content of `%s/%s` with hash `%s` was not found during response creation."
          (name (type/type resource-handle)) (:id resource-handle)
          (:hash resource-handle)))

(defn- resource-content-not-found-anom [e]
  (assoc e
         ::anom/category ::anom/fault
         ::anom/message (resource-content-not-found-msg e)
         :fhir/issue "incomplete"))

(defn- pull [db handle]
  (-> (d/pull db handle)
      (ac/exceptionally resource-content-not-found-anom)))

(defmethod build-response-entry "POST"
  [{:keys [return-preference db] :as context}
   _
   {{:fhir/keys [type] :keys [id]} :resource :as entry}]
  (let [type (name type)]
    ;; if the resource handle with `id` exists, it was created in the
    ;; transaction because a new id is created for POST requests
    (if-let [handle (d/resource-handle db type id)]
      (if (identical? :blaze.preference.return/representation return-preference)
        (do-sync [resource (pull db handle)]
          (assoc (created-entry context type handle) :resource resource))
        (ac/completed-future (created-entry context type handle)))
      (let [if-none-exist (-> entry :request :ifNoneExist)
            clauses (conditional-clauses if-none-exist)
            handle (coll/first (d/type-query db type clauses))]
        (if (identical? :blaze.preference.return/representation return-preference)
          (do-sync [resource (pull db handle)]
            (assoc (noop-entry db handle) :resource resource))
          (ac/completed-future (noop-entry db handle)))))))

(defn- update-entry
  [{:keys [db] :as context} type tx-op old-handle {:keys [id] :as handle}]
  (let [tx (d/tx db (:t handle))
        vid (str (:blaze.db/t tx))
        created (and (not (iu/keep? tx-op))
                     (or (nil? old-handle) (identical? :delete (:op old-handle))))]
    {:fhir/type :fhir.Bundle/entry
     :response
     (cond->
      {:fhir/type :fhir.Bundle.entry/response
       :status (if created "201" "200")
       :etag (str "W/\"" vid "\"")
       :lastModified (:blaze.db.tx/instant tx)}
       created
       (assoc :location (location context type id vid)))}))

(defmethod build-response-entry "PUT"
  [{:keys [db return-preference] :as context}
   _
   {{:fhir/keys [type] :keys [id]} :resource :keys [tx-op]}]
  (let [type (name type)
        [new-handle old-handle] (into [] (take 2) (d/instance-history db type id))]
    (if (identical? :blaze.preference.return/representation return-preference)
      (do-sync [resource (pull db new-handle)]
        (assoc (update-entry context type tx-op old-handle new-handle) :resource resource))
      (ac/completed-future (update-entry context type tx-op old-handle new-handle)))))

(defmethod build-response-entry "DELETE"
  [{:keys [db]} _ _]
  (let [t (d/basis-t db)]
    (ac/completed-future
     {:fhir/type :fhir.Bundle/entry
      :response
      {:fhir/type :fhir.Bundle.entry/response
       :status "204"
       :etag (str "W/\"" t "\"")
       :lastModified (:blaze.db.tx/instant (d/tx db t))}})))

(defn- build-response-entries* [{:keys [db] :as context} entries]
  (with-open [batch-db (d/new-batch-db db)]
    (into [] (map-indexed (partial build-response-entry (assoc context :db batch-db))) entries)))

(defn- build-response-entries [context entries]
  (let [futures (build-response-entries* context entries)]
    (do-sync [_ (ac/all-of futures)]
      (mapv ac/join futures))))

(defn- convert-http-date
  "Converts string `s` representing an HTTP date into a FHIR instant."
  [s]
  (Instant/from (.parse DateTimeFormatter/RFC_1123_DATE_TIME s)))

(defn- bundle-response
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

(defn- batch-request
  [{:keys [context-path return-preference db] :blaze/keys [base-url]}
   {{:keys [method url identity]
     if-none-match :ifNoneMatch
     if-match :ifMatch
     if-none-exist :ifNoneExist}
    :request :keys [resource]}]
  (let [url (-> url type/value strip-leading-slash)
        [url query-string] (str/split url #"\?")
        method (keyword (str/lower-case (type/value method)))
        return-preference (or return-preference
                              (when (#{:post :put} method)
                                "minimal"))]
    (cond->
     {:uri (str context-path "/" url)
      :request-method method
      :blaze/base-url base-url}

      query-string
      (assoc :query-string query-string)

      return-preference
      (assoc-in [:headers "prefer"] (str "return=" return-preference))

      if-none-match
      (assoc-in [:headers "if-none-match"] if-none-match)

      if-match
      (assoc-in [:headers "if-match"] if-match)

      if-none-exist
      (assoc-in [:headers "if-none-exist"] if-none-exist)

      identity
      (assoc :identity identity)

      resource
      (assoc :body resource)

      db
      (assoc :blaze/db db))))

(defmethod build-response-entry "GET"
  [{:keys [batch-handler] :as context} idx entry]
  (-> (batch-handler (batch-request context entry))
      (ac/then-apply bundle-response)
      (ac/exceptionally (bundle-error-response idx))))

(defn- process-batch-entry
  "Returns a CompletableFuture that will complete with the response entry of
  processing `entry`."
  [{:keys [batch-handler] :as context} idx entry]
  (if-ok [_ (validate-entry idx entry)]
    (-> (batch-handler (batch-request context entry))
        (ac/then-apply bundle-response)
        (ac/exceptionally (bundle-error-response idx)))
    (comp ac/completed-future response-entry
          handler-util/bundle-error-response)))

(defmulti process-entries
  "Processes `entries` according the batch or transaction rules.

  In case of a batch bundle, returns a CompletableFuture that will complete with
  the response entries.

  In case of a transaction bundle, returns a CompletableFuture that will
  complete with the response entries or will complete exceptionally with an
  anomaly in case of errors."
  {:arglists '([context request entries])}
  (fn [_ {{:keys [type]} :body} _] (type/value type)))

(defmethod process-entries "batch"
  [context _ entries]
  (let [futures (map-indexed (partial process-batch-entry context) entries)]
    (do-sync [_ (ac/all-of futures)]
      (mapv ac/join futures))))

(defn- transact [{:keys [node] :as context} entries]
  (if-ok [entries (bundle/assoc-tx-ops (d/db node) entries)]
    (-> (let [tx-ops (bundle/tx-ops entries)]
          (if (empty? tx-ops)
            (d/sync node)
            (d/transact node tx-ops)))
        (ac/then-compose #(build-response-entries (assoc context :db %) entries)))
    ac/completed-future))

(defmethod process-entries "transaction"
  [context _ entries]
  (ac/retry2 #(transact context entries)
             #(and (ba/conflict? %) (= "keep" (-> % :blaze.db/tx-cmd :op))
                   (nil? (:http/status %)))))

(defn- process-context
  [context
   {:keys [batch-handler headers] :blaze/keys [base-url]
    ::reitit/keys [router match]}]
  (assoc context
         :blaze/base-url base-url
         ::reitit/router router
         :batch-handler batch-handler
         :context-path (-> match :data :blaze/context-path)
         :return-preference (handler-util/preference headers "return")))

(defn- response-bundle [context type entries]
  {:fhir/type :fhir/Bundle
   :id (iu/luid context)
   :type (type/code (str (type/value type) "-response"))
   :entry entries})

(defmethod ig/pre-init-spec :blaze.interaction/transaction [_]
  (s/keys :req-un [:blaze.db/node :blaze/clock :blaze/rng-fn]))

(defmethod ig/init-key :blaze.interaction/transaction [_ context]
  (log/info "Init FHIR transaction interaction handler")
  (fn [{{:keys [type] :as bundle} :body :as request}]
    (if-ok [bundle (validate-and-prepare-bundle context bundle)]
      (-> (if (empty? bundle)
            (ac/completed-future [])
            (process-entries (process-context context request) request bundle))
          (ac/then-apply #(ring/response (response-bundle context type %))))
      ac/completed-future)))
