(ns blaze.interaction.history.type
  "FHIR history interaction on the whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.history.util :as history-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.uuid :refer [random-uuid]]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- link [match query-params t relation resource-handle]
  {:fhir/type :fhir.Bundle/link
   :relation relation
   :url (type/->Uri (history-util/nav-url match query-params t
                                          (d/last-updated-t resource-handle)
                                          (:id resource-handle)))})


(defn- build-response
  [router match db query-params t total version-handles]
  (let [page-size (fhir-util/page-size query-params)
        paged-version-handles (into [] (take (inc page-size)) version-handles)]
    ;; we need take here again because we take page-size + 1 above
    (-> (d/pull-many db (take page-size paged-version-handles))
        (ac/then-apply
          (fn [paged-versions]
            (ring/response
              (cond->
                {:fhir/type :fhir/Bundle
                 :id (str (random-uuid))
                 :type #fhir/code"history"
                 :total (type/->UnsignedInt total)
                 :entry (mapv #(history-util/build-entry router %) paged-versions)}

                (seq paged-version-handles)
                (update :link (fnil conj []) (link match query-params t "self"
                                                   (first paged-version-handles)))

                (< page-size (count paged-version-handles))
                (update :link (fnil conj []) (link match query-params t "next"
                                                   (peek paged-version-handles))))))))))


(defn- handle [router match query-params db type]
  (let [t (or (d/as-of-t db) (d/basis-t db))
        page-t (history-util/page-t query-params)
        page-id (when page-t (fhir-util/page-id query-params))
        since (history-util/since query-params)
        total (d/total-num-of-type-changes db type since)
        version-handles (d/type-history db type page-t page-id since)]
    (build-response router match db query-params t total version-handles)))


(defn- handler-intern [node]
  (fn [{::reitit/keys [router match] :keys [query-params]
        {{:fhir.resource/keys [type]} :data} ::reitit/match}]
    (-> (handler-util/db node (fhir-util/t query-params))
        (ac/then-compose #(handle router match query-params % type)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-observe-request-duration "history-type")))


(defmethod ig/init-key :blaze.interaction.history/type
  [_ {:keys [node]}]
  (log/info "Init FHIR history type interaction handler")
  (handler node))
