(ns blaze.interaction.search-system
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.anomaly :as ba :refer [if-ok when-ok]]
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params :as params]
    [blaze.interaction.search.util :as search-util]
    [blaze.interaction.util :as iu]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- handles [{{:keys [page-type page-id]} :params} db]
  (if (and page-type page-id)
    (d/system-list db page-type page-id)
    (d/system-list db)))


(defn- entries
  [{:keys [base-url router] {:keys [page-size]} :params :as context} db]
  (let [handles (handles context db)]
    (-> (d/pull-many db (into [] (take (inc page-size)) handles))
        (ac/then-apply
          (fn [resources]
            (mapv #(search-util/entry base-url router %) resources))))))


(defn- self-link-offset [[{first-resource :resource}]]
  (when-let [{:fhir/keys [type] :keys [id]} first-resource]
    {"__page-type" (name type) "__page-id" id}))


(defn- self-link [{:keys [base-url match params]} t entries]
  {:fhir/type :fhir.Bundle/link
   :relation "self"
   :url (type/->Uri (nav/url base-url match params [] t
                             (self-link-offset entries)))})


(defn- next-link-offset [entries]
  (let [{:fhir/keys [type] :keys [id]} (:resource (peek entries))]
    {"__page-type" (name type) "__page-id" id}))


(defn- next-link [{:keys [base-url match params]} t entries]
  {:fhir/type :fhir.Bundle/link
   :relation "next"
   :url (type/->Uri (nav/url base-url match params [] t
                             (next-link-offset entries)))})


(defn- search-normal [{:keys [params] :as context} db]
  (let [t (or (d/as-of-t db) (d/basis-t db))]
    (-> (entries context db)
        (ac/then-apply
          (fn [entries]
            (let [page-size (:page-size params)]
              (cond->
                {:fhir/type :fhir/Bundle
                 :id (iu/luid context)
                 :type #fhir/code"searchset"
                 :total (type/->UnsignedInt (d/system-total db))
                 :entry (if (< page-size (count entries))
                          (pop entries)
                          entries)
                 :link [(self-link context t entries)]}

                (< page-size (count entries))
                (update :link conj (next-link context t entries)))))))))


(defn- search-summary [context db]
  (ac/completed-future
    {:fhir/type :fhir/Bundle
     :id (iu/luid context)
     :type #fhir/code"searchset"
     :total (type/->UnsignedInt (d/system-total db))
     :link [(self-link context (or (d/as-of-t db) (d/basis-t db)) [])]}))


(defn- search [{:keys [params] :as context} db]
  (if (:summary? params)
    (search-summary context db)
    (search-normal context db)))


(defn- search-context
  [context
   {{{:fhir.resource/keys [type]} :data :as match} ::reitit/match
    :keys [headers params]
    :blaze/keys [base-url]
    ::reitit/keys [router]}]
  (let [handling (handler-util/preference headers "handling")]
    (when-ok [params (params/decode handling params)]
      (assoc context
        :base-url base-url
        :router router
        :match match
        :type type
        :preference/handling handling
        :params params))))


(defn- handler [context]
  (fn [{:blaze/keys [db] :as request}]
    (if-ok [context (search-context context request)]
      (-> (search context db)
          (ac/then-apply ring/response))
      (comp ac/failed-future ba/ex-anom))))


(defmethod ig/pre-init-spec :blaze.interaction/search-system [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn]))


(defmethod ig/init-key :blaze.interaction/search-system [_ context]
  (log/info "Init FHIR search-system interaction handler")
  (-> (handler context)
      (wrap-observe-request-duration "search-system")))
