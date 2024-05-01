(ns blaze.interaction.history.instance
  "FHIR history interaction on a single resource.

  https://www.hl7.org/fhir/http.html#history"
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.history.util :as history-util]
   [blaze.interaction.util :as iu]
   [blaze.module :as m]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- match [router type name id]
  (reitit/match-by-name router (keyword type name) {:id id}))

(defn- next-link [context query-params resource-handle]
  {:fhir/type :fhir.Bundle/link
   :relation "next"
   :url (history-util/page-nav-url context query-params (:t resource-handle))})

(defn- build-response
  [{:blaze/keys [db] :as context} query-params total version-handles since]
  (let [page-size (fhir-util/page-size query-params)
        page-xform (history-util/page-xform db page-size since)
        paged-version-handles (into [] page-xform version-handles)
        next-link (partial next-link context query-params)]
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
              :link [(history-util/self-link context query-params)]
              :entry
              (mapv (partial history-util/build-entry context) paged-versions)}

              (< page-size (count paged-version-handles))
              (update :link conj (next-link (peek paged-version-handles))))))))))

(defmethod m/pre-init-spec :blaze.interaction.history/instance [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn]))

(defmethod ig/init-key :blaze.interaction.history/instance [_ context]
  (log/info "Init FHIR history instance interaction handler")
  (fn [{:blaze/keys [base-url db]
        ::reitit/keys [router] :keys [params]
        {{:fhir.resource/keys [type]} :data} ::reitit/match
        {:keys [id]} :path-params}]
    (if (d/resource-handle db type id)
      (let [page-t (history-util/page-t params)
            since (history-util/since params)
            total (d/total-num-of-instance-changes db type id since)
            version-handles (d/instance-history db type id page-t)
            context (assoc context
                           :blaze/base-url base-url
                           :blaze/db db
                           ::reitit/router router
                           ::reitit/match (match router type "history-instance" id)
                           ::reitit/page-match (match router type "history-instance-page" id))]
        (build-response context params total version-handles since))
      (ac/completed-future
       (ba/not-found
        (format "Resource `%s/%s` was not found." type id)
        :fhir/issue "not-found")))))
