(ns blaze.interaction.history.instance
  "FHIR history interaction on a single resource.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.history.util :as history-util]
    [blaze.luid :as luid]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- build-response
  [router match db query-params t total version-handles]
  (let [page-size (fhir-util/page-size query-params)
        paged-version-handles (into [] (take (inc page-size)) version-handles)
        self-link
        (fn [resource-handle]
          {:fhir/type :fhir.Bundle/link
           :relation "self"
           :url (type/->Uri (history-util/nav-url match query-params t (:t resource-handle)))})
        next-link
        (fn [resource-handle]
          {:fhir/type :fhir.Bundle/link
           :relation "next"
           :url (type/->Uri (history-util/nav-url match query-params t (:t resource-handle)))})]
    ;; we need take here again because we take page-size + 1 above
    (-> (d/pull-many db (take page-size paged-version-handles))
        (ac/then-apply
          (fn [paged-versions]
            (ring/response
              (cond->
                {:fhir/type :fhir/Bundle
                 :id (luid/luid)
                 :type #fhir/code"history"
                 :total (type/->UnsignedInt total)
                 :link []
                 :entry (mapv #(history-util/build-entry router %) paged-versions)}

                (first paged-version-handles)
                (update :link conj (self-link (first paged-version-handles)))

                (< page-size (count paged-version-handles))
                (update :link conj (next-link (peek paged-version-handles))))))))))


(defn handle [router match query-params db type id]
  (if (d/resource-handle db type id)
    (let [t (or (d/as-of-t db) (d/basis-t db))
          page-t (history-util/page-t query-params)
          since (history-util/since query-params)
          total (d/total-num-of-instance-changes db type id since)
          version-handles (d/instance-history db type id page-t since)]
      (build-response router match db query-params t total version-handles))
    (ac/completed-future
      (handler-util/error-response
        {::anom/category ::anom/not-found
         :fhir/issue "not-found"}))))


(defn- handler-intern [node]
  (fn [{::reitit/keys [router match] :keys [query-params]
        {{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params}]
    (-> (handler-util/db node (fhir-util/t query-params))
        (ac/then-compose #(handle router match query-params % type id)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-observe-request-duration "history-instance")))


(defmethod ig/pre-init-spec :blaze.interaction.history/instance [_]
  (s/keys :req-un [:blaze.db/node]))


(defmethod ig/init-key :blaze.interaction.history/instance
  [_ {:keys [node]}]
  (log/info "Init FHIR history instance interaction handler")
  (handler node))
