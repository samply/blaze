(ns blaze.interaction.history.system
  "FHIR history interaction on thw whole system.

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

(defn- match [router name]
  (reitit/match-by-name router name))

(defn- next-link
  [{::search-util/keys [link] :as context} query-params resource-handle]
  (->> (history-util/page-nav-url
        context query-params
        (:t resource-handle)
        (-> resource-handle :fhir/type name)
        (:id resource-handle))
       (link "next")))

(defn- build-response
  [{:blaze/keys [db] :as context} query-params page-t total handles]
  (let [page-size (fhir-util/page-size query-params)
        {:keys [handles next-handle]} (history-util/build-page page-size handles)
        next-link (partial next-link context query-params)]
    (-> (d/pull-many db handles (history-util/pull-opts query-params page-t))
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

(defmethod m/pre-init-spec :blaze.interaction.history/system [_]
  (s/keys :req [::search-util/link]
          :req-un [:blaze/clock :blaze/rng-fn :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.interaction.history/system [_ context]
  (log/info "Init FHIR history system interaction handler")
  (fn [{:blaze/keys [base-url db]
        ::reitit/keys [router]
        :keys [params]}]
    (let [page-t (history-util/page-t params)
          page-type (when page-t (fhir-util/page-type params))
          page-id (when page-type (fhir-util/page-id params))
          since (fhir-util/since params)
          db (cond-> db since (d/since since))
          total (d/total-num-of-system-changes db)
          version-handles (d/system-history db page-t page-type page-id)
          context (assoc context
                         :blaze/base-url base-url
                         :blaze/db db
                         ::reitit/router router
                         ::reitit/match (match router :history)
                         :page-match #(reitit/match-by-name router :history-page {:page-id %}))]
      (build-response context params page-t total version-handles))))
