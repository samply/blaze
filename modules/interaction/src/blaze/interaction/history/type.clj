(ns blaze.interaction.history.type
  "FHIR history interaction on the whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.handler.util :as util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.history.util :as history-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.db.api :as d]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- resource-t [resource]
  (-> resource meta :blaze.db/t))


(defn- build-response
  [router match query-params t total versions]
  (let [page-size (fhir-util/page-size query-params)
        paged-versions (into [] (take (inc page-size)) versions)
        self-link
        (fn [resource]
          {:relation "self"
           :url (history-util/nav-url match query-params t (resource-t resource)
                                      (:id resource))})
        next-link
        (fn [resource]
          {:relation "next"
           :url (history-util/nav-url match query-params t (resource-t resource)
                                      (:id resource))})]
    (ring/response
      (cond->
        {:resourceType "Bundle"
         :type "history"
         :total total
         :link []
         :entry
         (into
           []
           (comp
             ;; we need take here again because we take page-size + 1 above
             (take page-size)
             (map (partial history-util/build-entry router)))
           paged-versions)}

        (first paged-versions)
        (update :link conj (self-link (first paged-versions)))

        (< page-size (count paged-versions))
        (update :link conj (next-link (peek paged-versions)))))))


(defn- handle [router match query-params db type]
  (let [t (or (d/as-of-t db) (d/basis-t db))
        page-t (history-util/page-t query-params)
        page-id (when page-t (history-util/page-id query-params))
        since-inst (history-util/since-inst query-params)
        total (d/total-num-of-type-changes db type since-inst)
        versions (d/type-history db type page-t page-id since-inst)]
    (build-response router match query-params t total versions)))


(defn- handler-intern [node]
  (fn [{::reitit/keys [router match] :keys [query-params]
        {{:fhir.resource/keys [type]} :data} ::reitit/match}]
    (-> (util/db node (fhir-util/t query-params))
        (md/chain' #(handle router match query-params % type)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-params)
      (wrap-observe-request-duration "history-type")))


(defmethod ig/init-key :blaze.interaction.history/type
  [_ {:keys [node]}]
  (log/info "Init FHIR history type interaction handler")
  (handler node))
