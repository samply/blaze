(ns blaze.interaction.history.instance
  "FHIR history interaction on a single resource.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.datomic.util :as util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.history.util :as history-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]))


(defn- resource-eid [db type id]
  (:db/id (util/resource db type id)))


(defn- total [db since-t eid]
  (let [total (util/instance-version (d/entity db eid))]
    (if since-t
      (- total (util/instance-version (d/entity (d/as-of db since-t) eid)))
      total)))


(defn- build-response
  "The coll of `transactions` already starts at `page-t`."
  [router match query-params db since-t eid transactions]
  (let [page-size (fhir-util/page-size query-params)
        transactions (into [] (take (inc page-size) transactions))
        more-entries-available? (< page-size (count transactions))
        t (or (d/as-of-t db) (d/basis-t db))
        self-link
        (fn [transaction]
          {:relation "self"
           :url (history-util/nav-url match query-params t transaction nil)})
        next-link
        (fn [transaction]
          {:relation "next"
           :url (history-util/nav-url match query-params t transaction nil)})]
    (ring/response
      (cond->
        {:resourceType "Bundle"
         :type "history"
         :total (total db since-t eid)
         :link []
         :entry
         (into
           []
           (comp
             ;; we need take here again because we take page-size + 1 above
             (take page-size)
             (map #(history-util/build-entry router db % eid)))
           transactions)}

        (first transactions)
        (update :link conj (self-link (first transactions)))

        more-entries-available?
        (update :link conj (next-link (peek transactions)))))))


(defn handle [router match query-params db type id]
  (if-let [eid (resource-eid db type id)]
    (let [page-t (history-util/page-t query-params)
          since-t (history-util/since-t db query-params)
          tx-db (history-util/tx-db db since-t page-t)
          transactions (util/instance-transaction-history tx-db eid)]
      (build-response router match query-params db since-t eid transactions))
    (handler-util/error-response
      {::anom/category ::anom/not-found
       :fhir/issue "not-found"})))


(defn- handler-intern [conn]
  (fn [{::reitit/keys [router match] :keys [query-params]
        {:keys [type id]} :path-params}]
    (-> (handler-util/db conn (fhir-util/t query-params))
        (md/chain' #(handle router match query-params % type id)))))


(s/def :handler.fhir/history-instance fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn)
  :ret :handler.fhir/history-instance)

(defn handler
  ""
  [conn]
  (-> (handler-intern conn)
      (wrap-params)
      (wrap-observe-request-duration "history-instance")))
