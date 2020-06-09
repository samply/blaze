(ns blaze.interaction.history.util
  (:require
    [blaze.db.api-spec]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.spec]
    [clojure.spec.alpha :as s]
    [reitit.core :as reitit])
  (:import
    [java.time Instant]
    [java.time.format DateTimeParseException]))


(set! *warn-on-reflection* true)


(defn since-inst
  "Tries to parse a valid instant out of the `_since` query param.

  Returns nil on absent or invalid instant."
  {:arglists '([query-params])}
  [{since "_since"}]
  (when since
    (try
      (Instant/parse since)
      (catch DateTimeParseException _))))


(defn page-t
  "Returns the t (optional) to constrain the database in paging. Pages will
  start with a database as-of `page-t`."
  {:arglists '([query-params])}
  [{page-t "__page-t"}]
  (when (some->> page-t (re-matches #"\d+"))
    (Long/parseLong page-t)))


(defn page-type
  "Returns the `page-type` query param in case it is a valid FHIR resource type."
  {:arglists '([query-params])}
  [{page-type "__page-type"}]
  (when (some->> page-type (s/valid? :blaze.resource/resourceType))
    page-type))


(defn nav-url
  "Returns a nav URL which points to a page with it's first entry described by
  the specified values.

  Uses `match` to generate a link based on the current path with appended
  `query-params` and the extra paging params calculated from `t`, `page-t`,
  `type` and `id`."
  {:arglists
   '([match query-params t page-t]
     [match query-params t page-t id]
     [match query-params t page-t type id])}
  [{{:blaze/keys [base-url]} :data :as match} query-params t page-t & more]
  (let [path (reitit/match->path
               match
               (cond-> (assoc query-params "__t" t "__page-t" page-t)
                 (= 1 (count more))
                 (assoc "__page-id" (first more))
                 (= 2 (count more))
                 (assoc "__page-type" (first more) "__page-id" (second more))))]
    (str base-url path)))


(defn- method [resource]
  ((-> resource meta :blaze.db/op)
   {:create "POST"
    :put "PUT"
    :delete "DELETE"}))


(defn- url [router type id resource]
  (if (-> resource meta :blaze.db/op #{:create})
    (fhir-util/type-url router type)
    (fhir-util/instance-url router type id)))


(defn- status [resource]
  (let [meta (meta resource)]
    (cond
      (-> meta :blaze.db/op #{:create}) "201"
      (-> meta :blaze.db/op #{:delete}) "204"
      :else
      (if (= 1 (-> meta :blaze.db/num-changes)) "201" "200"))))


(defn- last-modified [{:blaze.db.tx/keys [instant]}]
  (str instant))


(defn build-entry [router {type :resourceType id :id :as resource}]
  (cond->
    {:fullUrl (fhir-util/instance-url router type id)
     :request
     {:method (method resource)
      :url (url router type id resource)}
     :response
     {:status (status resource)
      :etag (str "W/\"" (-> resource :meta :versionId) "\"")
      :lastModified (last-modified (-> resource meta :blaze.db/tx))}}
    (-> resource meta :blaze.db/op #{:delete} not)
    (assoc :resource resource)))
