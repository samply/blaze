(ns blaze.interaction.search-system
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.search.nav :as nav]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util.spec]
   [blaze.module :as m]
   [blaze.page-store.spec]
   [blaze.util :refer [conj-vec]]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- handles* [{:blaze/keys [db] {:keys [page-type page-id]} :params}]
  (if (and page-type page-id)
    (d/system-list db page-type page-id)
    (d/system-list db)))

(defn- handles [{{:keys [page-size]} :params :as context}]
  (into [] (take (inc page-size)) (handles* context)))

(defn- pull-opts [{:keys [pull-variant] {:keys [page-type]} :params}]
  (cond-> {:variant pull-variant} page-type (assoc :skip-cache-insertion? true)))

(defn- entries [{:blaze/keys [db] :as context}]
  (-> (d/pull-many db (handles context) (pull-opts context))
      (ac/exceptionally
       #(assoc %
               ::anom/category ::anom/fault
               :fhir/issue "incomplete"))
      (ac/then-apply
       (fn [resources]
         (mapv (partial search-util/match-entry context) resources)))))

(defn- self-link
  [{::search-util/keys [link] :keys [params] :blaze/keys [base-url]
    ::reitit/keys [match]}]
  (link "self" (nav/url base-url match params [])))

(defn- next-link-offset [entries]
  (let [{:fhir/keys [type] :keys [id]} (:resource (peek entries))]
    {"__page-type" (name type) "__page-id" id}))

(defn- next-link
  [{::search-util/keys [link] :keys [page-match params] :blaze/keys [db]
    :as context} entries]
  (do-sync [url (nav/token-url! context page-match params [] (d/t db)
                                (next-link-offset entries))]
    (link "next" url)))

(defn- normal-bundle
  [{:blaze/keys [db] {:keys [page-size]} :params
    {{route-name :name} :data} ::reitit/match :as context} entries]
  (cond->
   {:fhir/type :fhir/Bundle
    :id (m/luid context)
    :type #fhir/code "searchset"
    :total (type/unsignedInt (d/system-total db))
    :entry (if (< page-size (count entries))
             (pop entries)
             entries)}
    (not= :page route-name)
    (assoc :link [(self-link context)])))

(defn- search-normal [{{:keys [page-size]} :params :as context}]
  (-> (entries context)
      (ac/then-compose
       (fn [entries]
         (if (< page-size (count entries))
           (do-sync [next-link (next-link context entries)]
             (-> (normal-bundle context entries)
                 (update :link conj-vec next-link)))
           (-> (normal-bundle context entries)
               ac/completed-future))))))

(defn- search-summary [{:blaze/keys [db] :as context}]
  (ac/completed-future
   {:fhir/type :fhir/Bundle
    :id (m/luid context)
    :type #fhir/code "searchset"
    :total (type/unsignedInt (d/system-total db))
    :link [(self-link context)]}))

(defn- search [{:keys [params] :as context}]
  (if (:summary? params)
    (search-summary context)
    (search-normal context)))

(defn- search-context
  [{:keys [page-store] :as context}
   {:keys [headers params]
    :blaze/keys [base-url db]
    ::reitit/keys [router match]}]
  (let [handling (handler-util/preference headers "handling")]
    (do-sync [decoded-params (params/decode page-store handling params)]
      (assoc context
             :blaze/base-url base-url
             :blaze/db db
             ::reitit/router router
             ::reitit/match match
             :page-match #(reitit/match-by-name router :page {:page-id %})
             :params decoded-params
             :pull-variant (fhir-util/summary params)))))

(defmethod m/pre-init-spec :blaze.interaction/search-system [_]
  (s/keys :req [::search-util/link]
          :req-un [:blaze/clock :blaze/rng-fn :blaze/page-store
                   :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.interaction/search-system [_ context]
  (log/info "Init FHIR search-system interaction handler")
  (fn [request]
    (-> (search-context context request)
        (ac/then-compose search)
        (ac/then-apply ring/response))))
