(ns blaze.interaction.history.type
  "FHIR history interaction on the whole system.

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
  "Returns the total number of history entries of `type` in `db`, optional since
  some point in the past (`since-t`)."
  [db since-t type]
  (let [total (datomic-util/type-version db type)]
    (if since-t
      (- total (datomic-util/type-version (d/as-of db since-t) type))
      total)))


(defn- expand-resources
  "Returns a reducible coll of tuples of `transaction` and resource eid of
  resources of `type` changed in transaction starting possibly with the resource
  with `first-resource-eid`."
  [type transaction first-resource-eid]
  (let [db (d/entity-db transaction)]
    (eduction
      (map (fn [{:keys [v]}] v))
      (filter #(= type (datomic-util/entity-type* db %)))
      (map (fn [eid] [transaction eid]))
      (d/datoms db :eavt (:db/id transaction) :tx/resources first-resource-eid))))


(defn- entries [type [first-transaction & more-transactions] first-resource-eid page-size]
  (when first-transaction
    (let [first-entries (into [] (take page-size) (expand-resources type first-transaction first-resource-eid))]
      (into
        first-entries
        (comp
          (mapcat #(expand-resources type % nil))
          (take (- page-size (count first-entries))))
        more-transactions))))


(defn- build-response
  "The coll of `transactions` already starts at `page-t`."
  [router match query-params db since-t type transactions]
  (let [page-size (fhir-util/page-size query-params)
        entries (entries type transactions (history-util/page-eid query-params) (inc page-size))
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
         :total (total db since-t type)
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


(defn handle [router match query-params db type]
  (let [page-t (history-util/page-t query-params)
        since-t (history-util/since-t db query-params)
        tx-db (history-util/tx-db db since-t page-t)
        transactions (datomic-util/type-transaction-history tx-db type)]
    (build-response router match query-params db since-t type transactions)))


(defn- handler-intern [conn]
  (fn [{::reitit/keys [router match] :keys [query-params]
        {{:fhir.resource/keys [type]} :data} ::reitit/match}]
    (-> (util/db conn (fhir-util/t query-params))
        (md/chain' #(handle router match query-params % type)))))


(s/def :handler.fhir/history-type fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn)
  :ret :handler.fhir/history-type)

(defn handler
  ""
  [conn]
  (-> (handler-intern conn)
      (wrap-params)
      (wrap-observe-request-duration "history-type")))


(defmethod ig/init-key :blaze.interaction.history/type
  [_ {:database/keys [conn]}]
  (log/info "Init FHIR history type interaction handler")
  (handler conn))
