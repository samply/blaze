(ns blaze.interaction.history.type
  "FHIR history interaction on the whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.interaction.history.util :as history-util]
    [blaze.interaction.util :as iu]
    [blaze.spec]
    [blaze.util :refer [conj-vec]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- link [context query-params relation resource-handle]
  {:fhir/type :fhir.Bundle/link
   :relation relation
   :url (history-util/nav-url context query-params
                              (:t resource-handle)
                              (:id resource-handle))})


(defn- build-response
  [{:blaze/keys [db] :as context} query-params total version-handles]
  (let [page-size (fhir-util/page-size query-params)
        paged-version-handles (into [] (take (inc page-size)) version-handles)
        self-link (partial link context query-params "self")
        next-link (partial link context query-params "next")]
    ;; we need take here again because we take page-size + 1 above
    (-> (d/pull-many db (into [] (take page-size) paged-version-handles))
        (ac/exceptionally
          #(assoc %
             ::anom/category ::anom/fault
             :fhir/issue "incomplete"))
        (ac/then-apply
          (fn [paged-versions]
            (ring/response
              (cond->
                {:fhir/type :fhir/Bundle
                 :id (iu/luid context)
                 :type #fhir/code"history"
                 :total (type/->UnsignedInt total)
                 :entry
                 (mapv (partial history-util/build-entry context) paged-versions)}

                (seq paged-version-handles)
                (update :link conj-vec (self-link (first paged-version-handles)))

                (< page-size (count paged-version-handles))
                (update :link conj-vec (next-link (peek paged-version-handles))))))))))


(defmethod ig/pre-init-spec :blaze.interaction.history/type [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn]))


(defmethod ig/init-key :blaze.interaction.history/type [_ context]
  (log/info "Init FHIR history type interaction handler")
  (fn [{:blaze/keys [base-url db]
        ::reitit/keys [router match]
        :keys [query-params]
        {{:fhir.resource/keys [type]} :data} ::reitit/match}]
    (let [page-t (history-util/page-t query-params)
          page-id (when page-t (fhir-util/page-id query-params))
          since (history-util/since query-params)
          total (d/total-num-of-type-changes db type since)
          version-handles (d/type-history db type page-t page-id since)
          context (assoc context
                    :blaze/base-url base-url
                    :blaze/db db
                    ::reitit/router router
                    ::reitit/match match)]
      (build-response context query-params total version-handles))))
