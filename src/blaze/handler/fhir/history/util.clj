(ns blaze.handler.fhir.history.util
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as datomic-util]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [reitit.core :as reitit])
  (:import
    [java.time Instant]
    [java.util Date]))


(defn since-t
  "Uses the `_since` param to derive the since-t of `db`."
  {:arglists '([db params])}
  [db {since "_since"}]
  (when since
    (d/since-t (d/since db (Date/from (Instant/parse since))))))


(s/fdef page-t
  :args (s/cat :query-params (s/map-of string? string?))
  :ret (s/nilable nat-int?))

(defn page-t
  "Returns the t (optional) to constrain the database in paging. Pages will
  start with a database as-of `page-t`."
  {:arglists '([query-params])}
  [{:strs [page-t]}]
  (when (some->> page-t (re-matches #"\d+"))
    (Long/parseLong page-t)))


(s/fdef page-eid
  :args (s/cat :query-params (s/map-of string? string?))
  :ret (s/nilable ::ds/entity-id))

(defn page-eid
  ""
  {:arglists '([query-params])}
  [{:strs [page-eid]}]
  (when (some->> page-eid (re-matches #"\d+"))
    (Long/parseLong page-eid)))


(s/fdef nav-link
  :args (s/cat :base-uri string? :match some?
               :query-params (s/map-of string? string?)
               :relation string? :entry (s/tuple ::ds/entity ::ds/entity-id)))

(defn nav-link
  "Returns a nav link with `relation` and `entry` as first entry of the page."
  {:arglists '([base-uri match query-params relation entry])}
  [base-uri match query-params relation [transaction eid]]
  (let [t (d/tx->t (:db/id transaction))
        path (reitit/match->path match (assoc query-params "t" t "eid" eid))]
    {:relation relation
     :url (str base-uri path)}))


(defn- method [resource]
  (cond
    (datomic-util/initial-version-server-assigned-id? resource) "POST"
    (datomic-util/deleted? resource) "DELETE"
    :else "PUT"))


(defn- url [base-uri type id resource]
  (cond-> (str base-uri "/fhir/" type)
    (not (datomic-util/initial-version-server-assigned-id? resource))
    (str "/" id)))


(defn- status [resource]
  (cond
    (datomic-util/initial-version? resource) "201"
    (datomic-util/deleted? resource) "204"
    :else "200"))


(s/fdef build-entry
  :args (s/cat :base-uri string? :db ::ds/db :transaction ::ds/entity
               :resource-eid ::ds/entity-id))

(defn build-entry [base-uri db transaction resource-eid]
  (let [t (d/tx->t (:db/id transaction))
        db (d/as-of db t)
        resource (d/entity db resource-eid)
        [type id] (datomic-util/literal-reference resource)]
    (cond->
      {:fullUrl (str base-uri "/fhir/" type "/" id)
       :request
       {:method (method resource)
        :url (url base-uri type id resource)}
       :response
       {:status (status resource)
        :etag (str "W/\"" t "\"")
        :lastModified (str (datomic-util/tx-instant transaction))}}
      (not (datomic-util/deleted? resource))
      (assoc :resource (pull/pull-resource* db type resource)))))
