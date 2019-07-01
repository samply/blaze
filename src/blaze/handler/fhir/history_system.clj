(ns blaze.handler.fhir.history-system
  "FHIR history interaction on thw whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.datomic.util :as datomic-util]
    [blaze.handler.fhir.history.util :as history-util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]))


(defn- total
  "Returns the total number of system history entries in `db`, optional since
  some point in the past (`since-t`)."
  [db since-t]
  (let [total (datomic-util/system-version db)]
    (if since-t
      (- total (datomic-util/system-version (d/as-of db since-t)))
      total)))


(defn- expand-resources
  "Returns tuples of `transaction` and resource eid of resources changed in
  transaction."
  [first-transaction last-resource-eid transaction]
  (let [db (d/entity-db transaction)]
    (eduction
      (map (fn [{:keys [v]}] [transaction v]))
      (if (and (= first-transaction transaction) last-resource-eid)
        (d/datoms db :eavt (:db/id transaction) :tx/resources last-resource-eid)
        (d/datoms db :eavt (:db/id transaction) :tx/resources)))))


(defn- entries [last-resource-eid page-size transactions]
  (into
    []
    (comp
      (mapcat #(expand-resources (first transactions) last-resource-eid %))
      (take page-size))
    transactions))


(defn- build-response [base-uri db match since-t query-params transactions]
  (let [page-size (fhir-util/page-size query-params)
        entries (entries (history-util/page-eid query-params) (inc page-size) transactions)
        more-entries-available? (< page-size (count entries))]
    (ring/response
      (cond->
        {:resourceType "Bundle"
         :type "history"
         :total (total db since-t)
         :link [(history-util/nav-link base-uri match query-params "self" (first entries))]
         :entry
         (into
           []
           (comp
             (take page-size)
             (map (fn [[tx eid]] (history-util/build-entry base-uri db tx eid))))
           entries)}

        more-entries-available?
        (update :link conj
                (history-util/nav-link base-uri match query-params "next" (peek entries)))))))


(defn- tx-db
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


(defn- handle [base-uri db match query-params t]
  (let [since-t (history-util/since-t db query-params)
        tx-db (tx-db db since-t t)
        transactions (datomic-util/system-transaction-history tx-db)]
    (build-response base-uri db match since-t query-params transactions)))


(defn- db [conn t]
  (if t
    (d/sync conn t)
    (d/db conn)))


(defn- handler-intern [base-uri conn]
  (fn [{:keys [query-params] ::reitit/keys [match]}]
    (let [t (history-util/page-t query-params)]
      (-> (db conn t)
          (md/chain #(handle base-uri % match query-params t))))))


(s/def :handler.fhir/history-system fn?)


(s/fdef handler
  :args (s/cat :base-uri string? :conn ::ds/conn)
  :ret :handler.fhir/history-system)

(defn handler
  ""
  [base-uri conn]
  (-> (handler-intern base-uri conn)
      (wrap-params)
      (wrap-observe-request-duration "history-system")))
