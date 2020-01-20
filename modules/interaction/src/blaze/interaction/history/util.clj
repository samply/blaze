(ns blaze.interaction.history.util
  (:require
    [blaze.datomic.pull :as pull]
    [blaze.datomic.util :as datomic-util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.spec]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [reitit.core :as reitit])
  (:import
    [java.time Instant]
    [java.util Date]))


(s/fdef since-t
  :args (s/cat :db ::ds/db :query-params (s/map-of string? string?))
  :ret (s/nilable nat-int?))

(defn since-t
  "Uses the `_since` param to derive the since-t of `db`."
  {:arglists '([db query-params])}
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
  "Returns the `page-eid` query param in case it is a valid integer."
  {:arglists '([query-params])}
  [{:strs [page-eid]}]
  (when (some->> page-eid (re-matches #"\d+"))
    (Long/parseLong page-eid)))


(s/fdef nav-url
  :args
  (s/cat
    :match :fhir.router/match
    :query-params (s/map-of string? string?)
    :t nat-int?
    :transaction ::ds/entity
    :eid (s/nilable ::ds/entity-id)))

(defn nav-url
  "Returns a nav URL with the entry of `transaction` and `eid` (optional) as
  first entry of the page.

  Uses `match` to generate a link based on the current path with appended
  `query-params` and the extra paging params calculated from `t` and entry."
  {:arglists '([match query-params t transaction eid])}
  [{{:blaze/keys [base-url]} :data :as match} query-params t transaction eid]
  (let [page-t (d/tx->t (:db/id transaction))
        path (reitit/match->path
               match
               (cond-> (assoc query-params "t" t "page-t" page-t)
                 eid (assoc "page-eid" eid)))]
    (str base-url path)))


(defn- method [resource]
  (cond
    (datomic-util/initial-version-server-assigned-id? resource) "POST"
    (datomic-util/deleted? resource) "DELETE"
    :else "PUT"))


(defn- url [router type id resource]
  (if (datomic-util/initial-version-server-assigned-id? resource)
    (fhir-util/type-url router type)
    (fhir-util/instance-url router type id)))


(defn- status [resource]
  (cond
    (datomic-util/initial-version? resource) "201"
    (datomic-util/deleted? resource) "204"
    :else "200"))


(s/fdef build-entry
  :args (s/cat :router reitit/router? :db ::ds/db :transaction ::ds/entity
               :resource-eid ::ds/entity-id))

(defn build-entry [router db transaction resource-eid]
  (let [t (d/tx->t (:db/id transaction))
        db (d/as-of db t)
        resource (d/entity db resource-eid)
        [type id] (datomic-util/literal-reference resource)]
    (cond->
      {:fullUrl (fhir-util/instance-url router type id)
       :request
       {:method (method resource)
        :url (url router type id resource)}
       :response
       {:status (status resource)
        :etag (str "W/\"" t "\"")
        :lastModified (str (datomic-util/tx-instant transaction))}}
      (not (datomic-util/deleted? resource))
      (assoc :resource (pull/pull-resource* db type resource)))))


(s/fdef tx-db
  :args (s/cat :db ::ds/db :since-t (s/nilable nat-int?) :page-t (s/nilable nat-int?))
  :ret ::ds/db)

(defn tx-db
  "Returns a database which includes resources since the optional `since-t` and
  up-to (as-of) the optional `page-t`. If both times are omitted, `db` is
  returned unchanged.

  While `page-t` is used for paging, restricting the database page by page more
  into the past, `since-t` is used to cut the database at some point in the past
  in order to include only resources up-to this point in time. So `page-t`
  should be always greater or equal to `since-t`."
  [db since-t page-t]
  (let [tx-db (if since-t (d/since db since-t) db)]
    (if page-t (d/as-of tx-db page-t) tx-db)))
