(ns blaze.interaction.search-compartment
  (:refer-clojure :exclude [str])
  (:require
   [blaze.anomaly :as ba :refer [if-ok when-ok]]
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.search.nav :as nav]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util.spec]
   [blaze.module :as m]
   [blaze.page-store.spec]
   [blaze.spec]
   [blaze.util :refer [str]]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- handles-and-clauses
  [{:keys [code id type] :blaze/keys [db] :blaze.preference/keys [handling]
    {:keys [clauses]} :params}]
  (cond
    (empty? clauses)
    {:handles (vec (d/list-compartment-resource-handles db code id type))}

    (identical? :blaze.preference.handling/strict handling)
    (when-ok [handles (d/compartment-query db code id type clauses)]
      {:handles (vec handles)
       :clauses clauses})

    :else
    (let [query (d/compile-compartment-query-lenient db code type clauses)]
      {:handles (vec (d/execute-query db query id))
       :clauses (d/query-clauses query)})))

(defn- entries-xf [{{:keys [page-offset page-size]} :params :as context}]
  (comp
   (drop page-offset)
   (take (inc page-size))
   (map (partial search-util/match-entry context))))

(defn- entries [context resources]
  (into [] (entries-xf context) resources))

(defn- self-link
  [{::search-util/keys [link] :keys [match params] :blaze/keys [base-url]}
   clauses]
  (link "self" (nav/url base-url match params clauses)))

(defn- next-link-offset [{:keys [page-offset]} entries]
  {"__page-offset" (str (+ page-offset (dec (count entries))))})

(defn- next-link
  [{::search-util/keys [link] :keys [page-match params]
    :blaze/keys [db] :as context} clauses entries]
  (do-sync [url (nav/token-url! context page-match params clauses (d/t db)
                                (next-link-offset params entries))]
    (link "next" url)))

(defn- bundle* [context handles clauses]
  {:fhir/type :fhir/Bundle
   :id (m/luid context)
   :type #fhir/code"searchset"
   :total (type/->UnsignedInt (count handles))
   :link [(self-link context clauses)]})

(defn- bundle [{{:keys [page-size]} :params :as context} handles clauses entries]
  (do-sync [next-link (next-link context clauses entries)]
    (cond->
     (assoc (bundle* context handles clauses)
            :entry (if (< page-size (count entries))
                     (pop entries)
                     entries))

      (< page-size (count entries))
      (update :link conj next-link))))

(defn- search-normal [{:blaze/keys [db] :as context}]
  (if-ok [{:keys [handles clauses]} (handles-and-clauses context)]
    (-> (d/pull-many db handles)
        (ac/exceptionally
         #(assoc %
                 ::anom/category ::anom/fault
                 :fhir/issue "incomplete"))
        (ac/then-apply (partial entries context))
        (ac/then-compose (partial bundle context handles clauses)))
    ac/completed-future))

(defn- search-summary [context]
  (when-ok [{:keys [handles clauses]} (handles-and-clauses context)]
    (bundle* context handles clauses)))

(defn- search [{:keys [params] :as context}]
  (if (:summary? params)
    (ac/completed-future (search-summary context))
    (search-normal context)))

(defn page-match [router code id type]
  #(reitit/match-by-name router (keyword code "compartment-page")
                         {:id id :type type :page-id %}))

(defn- search-context
  [{:keys [page-store] :as context}
   {{{:fhir.compartment/keys [code]} :data :as match} ::reitit/match
    {:keys [id type]} :path-params
    :keys [headers params]
    :blaze/keys [base-url db]
    ::reitit/keys [router]}]
  (cond
    (not (s/valid? :blaze.resource/id id))
    (ac/completed-future
     (ba/incorrect
      (format "The identifier `%s` is invalid." id)
      :fhir/issue "value"))

    (not (s/valid? :fhir.resource/type type))
    (ac/completed-future
     (ba/incorrect
      (format "The type `%s` is invalid." type)
      :fhir/issue "value"))

    :else
    (let [handling (handler-util/preference headers "handling")]
      (do-sync [params (params/decode page-store handling params)]
        (cond->
         (assoc context
                :blaze/base-url base-url
                :blaze/db db
                ::reitit/router router
                :match match
                :code code
                :id id
                :type type
                :page-match (page-match router code id type)
                :params params)
          handling
          (assoc :blaze.preference/handling handling))))))

(defmethod m/pre-init-spec :blaze.interaction/search-compartment [_]
  (s/keys :req [::search-util/link]
          :req-un [:blaze/clock :blaze/rng-fn :blaze/page-store
                   :blaze/page-id-cipher]))

(defmethod ig/init-key :blaze.interaction/search-compartment [_ context]
  (log/info "Init FHIR search-compartment interaction handler")
  (fn [request]
    (-> (search-context context request)
        (ac/then-compose search)
        (ac/then-apply ring/response))))
