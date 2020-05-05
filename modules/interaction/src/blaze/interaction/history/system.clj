(ns blaze.interaction.history.system
  "FHIR history interaction on thw whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.handler.util :as handler-util]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.history.util :as history-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.db.api :as d]
    [clojure.string :as str]
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
                                      (:resourceType resource) (:id resource))})
        next-link
        (fn [resource]
          {:relation "next"
           :url (history-util/nav-url match query-params t (resource-t resource)
                                      (:resourceType resource) (:id resource))})]
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


(defn- handle [router match query-params db]
  (let [t (or (d/as-of-t db) (d/basis-t db))
        page-t (history-util/page-t query-params)
        page-type (when page-t (history-util/page-type query-params))
        page-id (when page-type (history-util/page-id query-params))
        since-inst (history-util/since-inst query-params)
        total (d/total-num-of-system-changes db since-inst)
        versions (d/system-history db page-t page-type page-id since-inst)]
    (build-response router match query-params t total versions)))


(defn- handler-intern [node]
  (fn [{::reitit/keys [router match] :keys [query-params]}]
    (log/debug
      (if query-params
        (format "GET [base]/_history?%s"
              (->> (map (fn [[k v]] (format "%s=%s"k v)) query-params)
                   (str/join "&")))
        (format "GET [base]/_history")))
    (-> (handler-util/db node (fhir-util/t query-params))
        (md/chain' #(handle router match query-params %)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-params)
      (wrap-observe-request-duration "history-system")))


(defmethod ig/init-key :blaze.interaction.history/system
  [_ {:keys [node]}]
  (log/info "Init FHIR history system interaction handler")
  (handler node))
