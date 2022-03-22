(ns blaze.interaction.history.util
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.util :as iu]
    [reitit.core :as reitit])
  (:import
    [java.time Instant OffsetDateTime]
    [java.time.format DateTimeParseException]))


(set! *warn-on-reflection* true)


(defn since
  "Tries to parse a valid instant out of the `_since` query param.

  Returns nil on absent or invalid instant."
  {:arglists '([query-params])}
  [{v "_since"}]
  (some
    #(try
       (Instant/from (OffsetDateTime/parse %))
       (catch DateTimeParseException _))
    (fhir-util/to-seq v)))


(defn page-t
  "Returns the t (optional) to constrain the database in paging. Pages will
  start with a database as-of `page-t`."
  {:arglists '([query-params])}
  [{v "__page-t"}]
  (when-let [t (some #(when (re-matches #"\d+" %) %) (fhir-util/to-seq v))]
    (Long/parseLong t)))


(defn nav-url
  "Returns a nav URL which points to a page with it's first entry described by
  the specified values.

  Uses `match` to generate a link based on the current path with appended
  `query-params` and the extra paging params calculated from `t`, `page-t`,
  `type` and `id`."
  {:arglists
   '([context query-params t page-t]
     [context query-params t page-t id]
     [context query-params t page-t type id])}
  [{:blaze/keys [base-url db] ::reitit/keys [match]} query-params page-t & more]
  (let [path (reitit/match->path
               match
               (cond-> (assoc query-params "__t" (iu/t db) "__page-t" page-t)
                 (= 1 (count more))
                 (assoc "__page-id" (first more))
                 (= 2 (count more))
                 (assoc "__page-type" (first more) "__page-id" (second more))))]
    (str base-url path)))


(defn- method [resource]
  ((-> resource meta :blaze.db/op)
   {:create #fhir/code "POST"
    :put #fhir/code "PUT"
    :delete #fhir/code "DELETE"}))


(defn- url [context type id resource]
  (if (-> resource meta :blaze.db/op #{:create})
    (fhir-util/type-url context type)
    (fhir-util/instance-url context type id)))


(defn- status [resource]
  (let [meta (meta resource)]
    (cond
      (-> meta :blaze.db/op #{:create}) "201"
      (-> meta :blaze.db/op #{:delete}) "204"
      :else
      (if (= 1 (-> meta :blaze.db/num-changes)) "201" "200"))))


(defn build-entry [context {:fhir/keys [type] :keys [id] :as resource}]
  (cond->
    {:fullUrl (type/uri (fhir-util/instance-url context (name type) id))
     :request
     {:fhir/type :fhir.Bundle.entry/request
      :method (method resource)
      :url (type/uri (url (assoc context :blaze/base-url "") (name type) id
                            resource))}
     :response
     {:fhir/type :fhir.Bundle.entry/response
      :status (status resource)
      :etag (str "W/\"" (-> resource :meta :versionId type/value) "\"")
      :lastModified (-> resource meta :blaze.db/tx :blaze.db.tx/instant)}}
    (-> resource meta :blaze.db/op #{:delete} not)
    (assoc :resource resource)))
