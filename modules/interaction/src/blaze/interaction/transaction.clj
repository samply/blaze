(ns blaze.interaction.transaction
  "FHIR batch/transaction interaction.

  https://www.hl7.org/fhir/http.html#transaction"
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.transaction.bundle :as bundle]
   [blaze.interaction.util :as iu]
   [blaze.luid :as luid]
   [blaze.module :as m]
   [blaze.rest-api :as-alias rest-api]
   [blaze.spec]
   [blaze.util :refer [str]]
   [blaze.util.clauses :as uc]
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
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(def ^:private validate-entry-xf
  (comp (map-indexed fhir-util/validate-entry)
        (halt-when ba/anomaly?)))

(defn- validate-entries [entries]
  (transduce validate-entry-xf conj [] entries))

(defn- prepare-entry [res {{:keys [method]} :request :as entry}]
  (case (type/value method)
    "POST"
    (let [entry (update entry :resource assoc :id (luid/head (::luid/generator res)))]
      (-> (update res ::luid/generator luid/next)
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
               {::luid/generator (m/luid-generator context) :entries []}
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
  [{:blaze/keys [db] :as context} type {:keys [id] :as handle}]
  (let [tx (d/tx db (:t handle))
        vid (str (:blaze.db/t tx))]
    {:fhir/type :fhir.Bundle/entry
     :response
     {:fhir/type :fhir.Bundle.entry/response
      :status #fhir/string "201"
      :location (type/uri (location context type id vid))
      :etag (type/string (str "W/\"" vid "\""))
      :lastModified (:blaze.db.tx/instant tx)}}))

(defn- noop-entry [db handle]
  (let [tx (d/tx db (:t handle))
        vid (str (:blaze.db/t tx))]
    {:fhir/type :fhir.Bundle/entry
     :response
     {:fhir/type :fhir.Bundle.entry/response
      :status #fhir/string "200"
      :etag (type/string (str "W/\"" vid "\""))
      :lastModified (:blaze.db.tx/instant tx)}}))

(defn- conditional-clauses [if-none-exist]
  (when-not (str/blank? if-none-exist)
    (-> if-none-exist ring-codec/form-decode uc/search-clauses)))

(defn- resource-content-not-found-msg [{:blaze.db/keys [resource-handle]}]
  (format "The transaction was successful but the resource content of `%s/%s` with hash `%s` was not found during response creation."
          (name (:fhir/type resource-handle)) (:id resource-handle)
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
  [{:blaze/keys [db] return-preference :blaze.preference/return :as context}
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
  [{:blaze/keys [db] :as context} type tx-op old-handle {:keys [id] :as handle}]
  (let [tx (d/tx db (:t handle))
        vid (str (:blaze.db/t tx))
        created (and (not (iu/keep? tx-op))
                     (or (nil? old-handle) (identical? :delete (:op old-handle))))]
    {:fhir/type :fhir.Bundle/entry
     :response
     (cond->
      {:fhir/type :fhir.Bundle.entry/response
       :status (type/string (if created "201" "200"))
       :etag (type/string (str "W/\"" vid "\""))
       :lastModified (:blaze.db.tx/instant tx)}
       created
       (assoc :location (type/uri (location context type id vid))))}))

(defmethod build-response-entry "PUT"
  [{:blaze/keys [db] return-preference :blaze.preference/return :as context}
   _
   {{:fhir/keys [type] :keys [id]} :resource :keys [tx-op]}]
  (let [type (name type)
        [new-handle old-handle] (into [] (take 2) (d/instance-history db type id))]
    (if (identical? :blaze.preference.return/representation return-preference)
      (do-sync [resource (pull db new-handle)]
        (assoc (update-entry context type tx-op old-handle new-handle) :resource resource))
      (ac/completed-future (update-entry context type tx-op old-handle new-handle)))))

(defmethod build-response-entry "DELETE"
  [{:blaze/keys [db]} _ _]
  (let [t (d/basis-t db)]
    (ac/completed-future
     {:fhir/type :fhir.Bundle/entry
      :response
      {:fhir/type :fhir.Bundle.entry/response
       :status #fhir/string "204"
       :etag (type/string (str "W/\"" t "\""))
       :lastModified (:blaze.db.tx/instant (d/tx db t))}})))

(defmethod build-response-entry "GET" [context idx entry]
  (fhir-util/process-batch-entry context idx entry))

(defn- build-response-entries* [context entries]
  (into [] (map-indexed (partial build-response-entry context)) entries))

(defn- handle-close [stage db]
  (ac/handle
   stage
   (fn [entries e]
     (let [res (if e e entries)]
       (.close ^AutoCloseable db)
       res))))

(defn- build-response-entries [{:blaze/keys [db] :as context} entries]
  (let [db (d/new-batch-db db)
        futures (build-response-entries* (assoc context :blaze/db db) entries)]
    (-> (ac/all-of futures)
        (ac/then-apply (fn [_] (mapv ac/join futures)))
        (handle-close db))))

(defmulti process-entries
  "Processes `entries` according the batch or transaction rules.

  In case of a batch bundle, returns a CompletableFuture that will complete with
  the response entries.

  In case of a transaction bundle, returns a CompletableFuture that will
  complete with the response entries or will complete exceptionally with an
  anomaly in case of errors."
  {:arglists '([context type entries])}
  (fn [_ type _] (type/value type)))

(defmethod process-entries "batch"
  [context _ entries]
  (fhir-util/process-batch-entries context entries))

(defn- transact [{:keys [node] :as context} entries]
  (if-ok [entries (bundle/assoc-tx-ops (d/db node) entries)]
    (-> (let [tx-ops (bundle/tx-ops entries)]
          (if (empty? tx-ops)
            (fhir-util/sync node (:db-sync-timeout context))
            (d/transact node tx-ops)))
        (ac/then-compose
         #(build-response-entries (assoc context :blaze/db %) entries)))
    ac/completed-future))

(defmethod process-entries "transaction"
  [context _ entries]
  (ac/retry2 #(transact context entries)
             #(and (ba/conflict? %) (= "keep" (-> % :blaze.db/tx-cmd :op))
                   (nil? (:http/status %)))))

(defn- process-context*
  [context {:keys [headers] :blaze/keys [base-url] ::reitit/keys [router match]}]
  (let [return-preference (handler-util/preference headers "return")]
    (cond->
     (assoc context
            :blaze/base-url base-url
            ::reitit/router router
            :context-path (-> match :data :blaze/context-path))
      return-preference
      (assoc :blaze.preference/return return-preference))))

(defn- process-context [context type request]
  (if (= "batch" (type/value type))
    (-> (fhir-util/sync (:node context) (:db-sync-timeout context))
        (ac/then-apply #(assoc (process-context* context request) :blaze/db %)))
    (ac/completed-future (process-context* context request))))

(defn- response-bundle [context type entries]
  {:fhir/type :fhir/Bundle
   :id (m/luid context)
   :type (type/code (str (type/value type) "-response"))
   :entry entries})

(defmethod m/pre-init-spec :blaze.interaction/transaction [_]
  (s/keys :req-un [:blaze.db/node ::rest-api/batch-handler :blaze/clock :blaze/rng-fn
                   ::rest-api/db-sync-timeout]))

(defmethod ig/init-key :blaze.interaction/transaction [_ context]
  (log/info "Init FHIR transaction interaction handler")
  (fn [{{:keys [type] :as bundle} :body :as request}]
    (if-ok [entries (validate-and-prepare-bundle context bundle)]
      (-> (if (empty? entries)
            (ac/completed-future [])
            (-> (process-context context type request)
                (ac/then-compose #(process-entries % type entries))))
          (ac/then-apply #(ring/response (response-bundle context type %))))
      ac/completed-future)))
