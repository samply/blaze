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
   [blaze.interaction.search.page :as page]
   [blaze.interaction.search.params :as params]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util.spec]
   [blaze.module :as m]
   [blaze.page-store.spec]
   [blaze.spec]
   [blaze.util :refer [str]]
   [blaze.value-set-expander.spec]
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
    (when-ok [query (d/compile-compartment-query db code type)
              handles (d/execute-query db query id)]
      {:query query
       :handles (vec handles)})

    (identical? :blaze.preference.handling/strict handling)
    (when-ok [query (d/compile-compartment-query db code type clauses)
              handles (d/execute-query db query id)]
      {:query query
       :handles (vec handles)
       :clauses clauses})

    :else
    (when-ok [query (d/compile-compartment-query-lenient db code type clauses)
              handles (d/execute-query db query id)]
      {:query query
       :handles (vec handles)
       :clauses (d/query-clauses query)})))

(defn- entries-xf [{{:keys [page-offset page-size]} :params :as context} query]
  (comp
   (drop page-offset)
   (take (inc page-size))
   (map (page/match-entry-fn context query))))

(defn- entries [context query resources]
  (into [] (entries-xf context query) resources))

(defn- self-link
  [{::search-util/keys [link] :keys [search-match params] :blaze/keys [base-url]}
   clauses]
  (link "self" (nav/url base-url search-match params
                        (nav/client-clauses params clauses))))

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
   :type #fhir/code "searchset"
   :total (type/unsignedInt (count handles))
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
  (if-ok [{:keys [query handles clauses]} (handles-and-clauses context)]
    (-> (d/pull-many db handles)
        (ac/exceptionally
         #(assoc %
                 ::anom/category ::anom/fault
                 :fhir/issue "incomplete"))
        (ac/then-apply (partial entries context query))
        (ac/then-compose (partial bundle context handles clauses)))
    ac/completed-future))

(defn- search-summary [context]
  (ac/completed-future
   (when-ok [{:keys [handles clauses]} (handles-and-clauses context)]
     (bundle* context handles clauses))))

(defn- search [{{:keys [summary? unsatisfiable? clauses]} :params :as context}]
  (cond
    unsatisfiable? (ac/completed-future (bundle* context [] clauses))
    summary? (search-summary context)
    :else (search-normal context)))

(defn- search-match [router code id type]
  (reitit/match-by-name router (keyword code "compartment")
                        {:id id :type type}))

(defn page-match [router code id type]
  #(reitit/match-by-name router (keyword code "compartment-page")
                         {:id id :type type :page-id %}))

(defn- search-context
  [{:keys [page-store value-set-expander] :as context}
   {{{:fhir.compartment/keys [code]} :data} ::reitit/match
    {:keys [id type]} :path-params
    :keys [headers params]
    :blaze/keys [base-url db paging-params]
    ::reitit/keys [router]}]
  (cond
    (not (s/valid? :fhir.resource/type type))
    (ac/completed-future
     (ba/incorrect
      (format "The type `%s` is invalid." type)
      :fhir/issue "value"))

    :else
    (let [handling (handler-util/preference headers "handling")]
      (do-sync [params (-> (params/decode page-store handling params paging-params)
                           (ac/then-compose (partial params/expand-value-sets value-set-expander type)))]
        (cond->
         (assoc context
                :blaze/base-url base-url
                :blaze/db db
                ::reitit/router router
                :code code
                :id id
                :type type
                :page-match (page-match router code id type)
                :search-match (search-match router code id type)
                :params params)
          handling
          (assoc :blaze.preference/handling handling))))))

(defmethod m/pre-init-spec :blaze.interaction/search-compartment [_]
  (s/keys :req [::search-util/link]
          :req-un [:blaze/clock :blaze/rng-fn :blaze/page-store
                   :blaze/page-id-cipher :blaze/value-set-expander]))

(defmethod ig/init-key :blaze.interaction/search-compartment [_ context]
  (log/info "Init FHIR search-compartment interaction handler")
  (fn [request]
    (-> (search-context context request)
        (ac/then-compose search)
        (ac/then-apply ring/response))))
