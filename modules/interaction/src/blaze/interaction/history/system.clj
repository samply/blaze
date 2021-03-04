(ns blaze.interaction.history.system
  "FHIR history interaction on thw whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.history.util :as history-util]
    [blaze.luid :as luid]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
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
           :url (type/->Uri (history-util/nav-url match query-params t
                                                  (:t resource-handle)
                                                  (-> resource-handle fhir-spec/fhir-type name)
                                                  (:id resource-handle)))})
        next-link
        (fn [resource-handle]
          {:fhir/type :fhir.Bundle/link
           :relation "next"
           :url (type/->Uri (history-util/nav-url match query-params t
                                                  (:t resource-handle)
                                                  (-> resource-handle fhir-spec/fhir-type name)
                                                  (:id resource-handle)))})]
    ;; we need take here again because we take page-size + 1 above
    (-> (d/pull-many db (take page-size paged-version-handles))
        (ac/then-apply
          (fn [pages-versions]
            (ring/response
              (cond->
                {:fhir/type :fhir/Bundle
                 :id (luid/luid)
                 :type #fhir/code"history"
                 :total (type/->UnsignedInt total)
                 :entry (mapv #(history-util/build-entry router %) pages-versions)}

                (first paged-version-handles)
                (update :link (fnil conj []) (self-link (first paged-version-handles)))

                (< page-size (count paged-version-handles))
                (update :link (fnil conj []) (next-link (peek paged-version-handles))))))))))


(defn- handle [router match query-params db]
  (let [t (or (d/as-of-t db) (d/basis-t db))
        page-t (history-util/page-t query-params)
        page-type (when page-t (fhir-util/page-type query-params))
        page-id (when page-type (fhir-util/page-id query-params))
        since (history-util/since query-params)
        total (d/total-num-of-system-changes db since)
        version-handles (d/system-history db page-t page-type page-id since)]
    (build-response router match db query-params t total version-handles)))


(defn- handler-intern [node]
  (fn [{::reitit/keys [router match] :keys [query-params]}]
    (-> (handler-util/db node (fhir-util/t query-params))
        (ac/then-compose #(handle router match query-params %)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-observe-request-duration "history-system")))


(defmethod ig/pre-init-spec :blaze.interaction.history/system [_]
  (s/keys :req-un [:blaze.db/node]))


(defmethod ig/init-key :blaze.interaction.history/system
  [_ {:keys [node]}]
  (log/info "Init FHIR history system interaction handler")
  (handler node))
