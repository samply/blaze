(ns blaze.interaction.history.type
  "FHIR history interaction on the whole system.

  https://www.hl7.org/fhir/http.html#history"
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.history.util :as history-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util.spec]
   [blaze.module :as m]
   [blaze.page-id-cipher.spec]
   [blaze.spec]
   [blaze.util :refer [conj-vec]]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- next-link
  [{::search-util/keys [link] :as context} query-params resource-handle]
  (->> (history-util/page-nav-url context query-params
                                  (:t resource-handle)
                                  (:id resource-handle))
       (link "next")))

(defn- build-response
  [{:blaze/keys [db] :as context} query-params total handles]
  (let [page-size (fhir-util/page-size query-params)
        {:keys [handles next-handle]} (history-util/build-page page-size handles)
        next-link (partial next-link context query-params)]
    (-> (d/pull-many db handles (history-util/pull-opts query-params))
        (ac/exceptionally
         #(assoc %
                 ::anom/category ::anom/fault
                 :fhir/issue "incomplete"))
        (ac/then-apply
         (fn [paged-versions]
           (ring/response
            (cond->
             (assoc
              (history-util/build-bundle context total query-params)
              :entry
              (mapv (partial history-util/build-entry context) paged-versions))

              next-handle
              (update :link conj-vec (next-link next-handle)))))))))

(defmethod m/pre-init-spec :blaze.interaction.history/type [_]
  (s/keys :req [::search-util/link]
          :req-un [:blaze/clock :blaze/rng-fn :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.interaction.history/type [_ context]
  (log/info "Init FHIR history type interaction handler")
  (fn [{:blaze/keys [base-url db]
        ::reitit/keys [router]
        :keys [params]
        {{:fhir.resource/keys [type]} :data} ::reitit/match}]
    (let [page-t (history-util/page-t params)
          page-id (when page-t (fhir-util/page-id params))
          since (fhir-util/since params)
          db (cond-> db since (d/since since))
          total (d/total-num-of-type-changes db type)
          version-handles (d/type-history db type page-t page-id)
          context (assoc context
                         :blaze/base-url base-url
                         :blaze/db db
                         ::reitit/router router
                         ::reitit/match (reitit/match-by-name router (keyword type "history"))
                         :page-match #(reitit/match-by-name router (keyword type "history-page") {:page-id %}))]
      (build-response context params total version-handles))))
