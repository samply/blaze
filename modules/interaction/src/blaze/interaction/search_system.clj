(ns blaze.interaction.search-system
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params :as params]
    [blaze.interaction.search.util :as search-util]
    [blaze.interaction.util :as iu]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.page-store.spec]
    [clojure.spec.alpha :as s]
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


(defn- entries [{:blaze/keys [db] :as context}]
  (do-sync [resources (d/pull-many db (handles context))]
    (mapv (partial search-util/entry context) resources)))


(defn- self-link-offset [[{first-resource :resource}]]
  (when-let [{:fhir/keys [type] :keys [id]} first-resource]
    {"__page-type" (name type) "__page-id" id}))


(defn- self-link [{:keys [match params] :blaze/keys [base-url db]} entries]
  {:fhir/type :fhir.Bundle/link
   :relation "self"
   :url (type/uri (nav/url base-url match params [] (iu/t db)
                             (self-link-offset entries)))})


(defn- next-link-offset [entries]
  (let [{:fhir/keys [type] :keys [id]} (:resource (peek entries))]
    {"__page-type" (name type) "__page-id" id}))


(defn- next-link
  [{:keys [page-store page-match params] :blaze/keys [base-url db]} entries]
  (do-sync [url (nav/token-url page-store base-url page-match params []
                               (iu/t db) (next-link-offset entries))]
    {:fhir/type :fhir.Bundle/link
     :relation "next"
     :url (type/uri url)}))


(defn- normal-bundle
  [{:blaze/keys [db] {:keys [page-size]} :params :as context} entries]
  {:fhir/type :fhir/Bundle
   :id (iu/luid context)
   :type #fhir/code "searchset"
   :total (type/->UnsignedInt (d/system-total db))
   :entry (if (< page-size (count entries))
            (pop entries)
            entries)
   :link [(self-link context entries)]})


(defn- search-normal [{{:keys [page-size]} :params :as context}]
  (-> (entries context)
      (ac/then-compose
        (fn [entries]
          (if (< page-size (count entries))
            (do-sync [next-link (next-link context entries)]
              (-> (normal-bundle context entries)
                  (update :link conj next-link)))
            (-> (normal-bundle context entries)
                ac/completed-future))))))


(defn- search-summary [{:blaze/keys [db] :as context}]
  (ac/completed-future
    {:fhir/type :fhir/Bundle
     :id (iu/luid context)
     :type #fhir/code "searchset"
     :total (type/->UnsignedInt (d/system-total db))
     :link [(self-link context [])]}))


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
    (do-sync [params (params/decode page-store handling params)]
      (assoc context
        :blaze/base-url base-url
        :blaze/db db
        ::reitit/router router
        :match match
        :page-match (reitit/match-by-name router :page)
        :params params))))


(defn- handler [context]
  (fn [request]
    (-> (search-context context request)
        (ac/then-compose search)
        (ac/then-apply ring/response))))


(defmethod ig/pre-init-spec :blaze.interaction/search-system [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn :blaze/page-store]))


(defmethod ig/init-key :blaze.interaction/search-system [_ context]
  (log/info "Init FHIR search-system interaction handler")
  (-> (handler context)
      (wrap-observe-request-duration "search-system")))
