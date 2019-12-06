(ns blaze.interaction.history.system
  "FHIR history interaction on thw whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.datomic.util :as datomic-util]
    [blaze.handler.util :as util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.history.util :as history-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- total
  "Returns the total number of system history entries in `db`, optional since
  some point in the past (`since-t`)."
  [db since-t]
  (let [total (datomic-util/system-version db)]
    (if since-t
      (- total (datomic-util/system-version (d/as-of db since-t)))
      total)))


(defn- expand-resources
  "Returns a reducible collection of tuples of `transaction` and resource eid of
  resources changed in transaction starting possibly with the resource with
  `first-resource-eid`."
  [transaction first-resource-eid]
  (let [db (d/entity-db transaction)]
    (eduction
      (map (fn [{:keys [v]}] [transaction v]))
      (d/datoms db :eavt (:db/id transaction) :tx/resources first-resource-eid))))


(defn- entries [[first-transaction & more-transactions] first-resource-eid page-size]
  (when first-transaction
    (let [first-entries (into [] (take page-size) (expand-resources first-transaction first-resource-eid))]
      (into
        first-entries
        (comp
          (mapcat #(expand-resources % nil))
          (take (- page-size (count first-entries))))
        more-transactions))))


(defn- build-response
  "The coll of `transactions` already starts at `page-t`."
  [router match query-params db since-t transactions]
  (let [page-size (fhir-util/page-size query-params)
        entries (entries transactions (history-util/page-eid query-params) (inc page-size))
        more-entries-available? (< page-size (count entries))
        t (or (d/as-of-t db) (d/basis-t db))
        self-link
        (fn [[transaction eid]]
          {:relation "self"
           :url (history-util/nav-url match query-params t transaction eid)})
        next-link
        (fn [[transaction eid]]
          {:relation "next"
           :url (history-util/nav-url match query-params t transaction eid)})]
    (ring/response
      (cond->
        {:resourceType "Bundle"
         :type "history"
         :total (total db since-t)
         :link []
         :entry
         (into
           []
           (comp
             ;; we need take here again because we take page-size + 1 above
             (take page-size)
             (map (fn [[tx eid]] (history-util/build-entry router db tx eid))))
           entries)}

        (first entries)
        (update :link conj (self-link (first entries)))

        more-entries-available?
        (update :link conj (next-link (peek entries)))))))


(defn- handle [router match query-params db]
  (let [page-t (history-util/page-t query-params)
        since-t (history-util/since-t db query-params)
        tx-db (history-util/tx-db db since-t page-t)
        transactions (datomic-util/system-transaction-history tx-db)]
    (build-response router match query-params db since-t transactions)))


(defn- handler-intern [conn]
  (fn [{::reitit/keys [router match] :keys [query-params]}]
    (-> (util/db conn (fhir-util/t query-params))
        (md/chain' #(handle router match query-params %)))))


(s/def :handler.fhir/history-system fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn)
  :ret :handler.fhir/history-system)

(defn handler
  ""
  [conn]
  (-> (handler-intern conn)
      (wrap-params)
      (wrap-observe-request-duration "history-system")))


(defmethod ig/init-key :blaze.interaction.history/system
  [_ {:database/keys [conn]}]
  (log/info "Init FHIR history system interaction handler")
  (handler conn))
