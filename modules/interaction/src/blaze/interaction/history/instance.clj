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
    [blaze.interaction.util :as iu]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- link [base-url match query-params t relation resource-handle]
  {:fhir/type :fhir.Bundle/link
   :relation relation
   :url (type/->Uri (history-util/nav-url base-url match query-params t
                                          (:t resource-handle)))})


(defn- build-response
  [context db base-url router match query-params t total version-handles]
  (let [page-size (fhir-util/page-size query-params)
        paged-version-handles (into [] (take (inc page-size)) version-handles)
        self-link #(link base-url match query-params t "self" %)
        next-link #(link base-url match query-params t "next" %)]
    ;; we need take here again because we take page-size + 1 above
    (-> (d/pull-many db (take page-size paged-version-handles))
        (ac/then-apply
          (fn [paged-versions]
            (ring/response
              (cond->
                {:fhir/type :fhir/Bundle
                 :id (iu/luid context)
                 :type #fhir/code"history"
                 :total (type/->UnsignedInt total)
                 :link []
                 :entry
                 (mapv #(history-util/build-entry base-url router %)
                       paged-versions)}

                (first paged-version-handles)
                (update :link conj (self-link (first paged-version-handles)))

                (< page-size (count paged-version-handles))
                (update :link conj (next-link (peek paged-version-handles))))))))))


(defn- handler [context]
  (fn [{:blaze/keys [base-url db]
        ::reitit/keys [router match] :keys [query-params]
        {{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params}]
    (if (d/resource-handle db type id)
      (let [t (or (d/as-of-t db) (d/basis-t db))
            page-t (history-util/page-t query-params)
            since (history-util/since query-params)
            total (d/total-num-of-instance-changes db type id since)
            version-handles (d/instance-history db type id page-t since)]
        (build-response context db base-url router match query-params t total
                        version-handles))
      (ac/completed-future
        (handler-util/error-response
          {::anom/category ::anom/not-found
           :fhir/issue "not-found"})))))


(defmethod ig/pre-init-spec :blaze.interaction.history/instance [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn]))


(defmethod ig/init-key :blaze.interaction.history/instance [_ context]
  (log/info "Init FHIR history instance interaction handler")
  (-> (handler context)
      (wrap-observe-request-duration "history-instance")))
