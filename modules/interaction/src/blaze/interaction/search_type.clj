(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.anomaly :refer [if-ok when-ok]]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.search.include :as include]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params :as params]
    [blaze.interaction.search.util :as search-util]
    [blaze.interaction.util :as iu]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.page-store.spec]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- type-list [db type page-id]
  (if page-id
    (d/type-list db type page-id)
    (d/type-list db type)))


(defn- type-query [db type clauses page-id]
  (if page-id
    (d/type-query db type clauses page-id)
    (d/type-query db type clauses)))


(defn- execute-query [db query page-id]
  (if page-id
    (d/execute-query db query page-id)
    (d/execute-query db query)))


(defn- handles-and-clauses
  [{:keys [type] :blaze.preference/keys [handling]
    {:keys [clauses page-id]} :params}
   db]
  (cond
    (empty? clauses)
    {:handles (type-list db type page-id)}

    (identical? :blaze.preference.handling/strict handling)
    (when-ok [handles (type-query db type clauses page-id)]
      {:handles handles
       :clauses clauses})

    :else
    (when-ok [query (d/compile-type-query-lenient db type clauses)]
      {:handles (execute-query db query page-id)
       :clauses (d/query-clauses query)})))


(defn- build-matches-only-page [page-size handles]
  (let [handles (into [] (take (inc page-size)) handles)]
    (if (< page-size (count handles))
      {:matches (pop handles)
       :next-match (peek handles)}
      {:matches handles})))


(defn- build-page [db include-defs page-size handles]
  (if (:direct include-defs)
    (let [handles (into [] (take (inc page-size)) handles)]
      (if (< page-size (count handles))
        (let [page-handles (pop handles)]
          {:matches page-handles
           :includes (include/add-includes db include-defs page-handles)
           :next-match (peek handles)})
        {:matches handles
         :includes (include/add-includes db include-defs handles)}))
    (build-matches-only-page page-size handles)))


(defn- entries [context matches includes]
  (log/trace "build entries")
  (-> (mapv #(search-util/entry context %) matches)
      (into (map #(search-util/entry context % search-util/include)) includes)))


(defn- page-data
  "Returns a CompletableFuture that will complete with a map of:

  :entries - the bundle entries of the page
  :num-matches - the number of search matches (excluding includes)
  :next-handle - the resource handle of the first resource of the next page
  :clauses - the actually used clauses"
  {:arglists '([context db])}
  [{:blaze/keys [db] {:keys [include-defs page-size]} :params :as context}]
  (if-ok [{:keys [handles clauses]} (handles-and-clauses context db)]
    (let [{:keys [matches includes next-match]}
          (build-page db include-defs page-size handles)
          match-futures (mapv #(d/pull-many db %) (partition-all 100 matches))
          include-futures (mapv #(d/pull-many db %) (partition-all 100 includes))]
      (do-sync [_ (ac/all-of (into match-futures include-futures))]
        {:entries
         (entries
           context
           (mapcat deref match-futures)
           (mapcat deref include-futures))
         :num-matches (count matches)
         :next-handle next-match
         :clauses clauses}))
    ac/completed-future))


(defn- self-link-offset [first-entry]
  (when-let [id (-> first-entry :resource :id)]
    {"__page-id" id}))


(defn- self-link [{:keys [self-link-url-fn]} clauses first-entry]
  (let [url (self-link-url-fn clauses (self-link-offset first-entry))]
    {:fhir/type :fhir.Bundle/link
     :relation "self"
     :url (type/uri url)}))


(defn- next-link-offset [next-handle]
  {"__page-id" (:id next-handle)})


(defn- next-link
  [{:keys [next-link-url-fn]} clauses next-handle]
  (do-sync [url (next-link-url-fn clauses (next-link-offset next-handle))]
    {:fhir/type :fhir.Bundle/link
     :relation "next"
     :url (type/uri url)}))


(defn- total
  "Calculates the total number of resources returned.

  If we have no clauses (returning all resources), we can use `d/type-total`.
  Secondly, if the number of entries found is not more than one page in size,
  we can use that number. Otherwise, there is no cheap way to calculate the
  number of matching resources, so we don't report it."
  [{:keys [type] :blaze/keys [db] {:keys [clauses page-id]} :params}
   num-matches next-handle]
  (cond
    (empty? clauses)
    (d/type-total db type)

    (and (nil? page-id) (nil? next-handle))
    num-matches))


(defn- normal-bundle [context clauses entries total]
  (cond->
    {:fhir/type :fhir/Bundle
     :id (iu/luid context)
     :type #fhir/code"searchset"
     :entry entries
     :link [(self-link context clauses (first entries))]}

    total
    (assoc :total (type/->UnsignedInt total))))


(defn- search-normal [context]
  (-> (page-data context)
      (ac/then-compose
        (fn [{:keys [entries num-matches next-handle clauses]}]
          (let [total (total context num-matches next-handle)]
            (if next-handle
              (do-sync [next-link (next-link context clauses next-handle)]
                (-> (normal-bundle context clauses entries total)
                    (update :link conj next-link)))
              (-> (normal-bundle context clauses entries total)
                  ac/completed-future)))))))


(defn- summary-total
  [{:keys [type] :blaze/keys [db] :blaze.preference/keys [handling]
    {:keys [clauses]} :params}]
  (cond
    (empty? clauses)
    {:total (d/type-total db type)}

    (identical? :blaze.preference.handling/strict handling)
    (when-ok [handles (d/type-query db type clauses)]
      {:total (count handles)
       :clauses clauses})

    :else
    (when-ok [query (d/compile-type-query-lenient db type clauses)]
      {:total (count (d/execute-query db query))
       :clauses (d/query-clauses query)})))


(defn- search-summary [context]
  (when-ok [{:keys [total clauses]} (summary-total context)]
    {:fhir/type :fhir/Bundle
     :id (iu/luid context)
     :type #fhir/code"searchset"
     :total (type/->UnsignedInt total)
     :link [(self-link context clauses [])]}))


(defn- search [{:keys [params] :as context}]
  (if (:summary? params)
    (ac/completed-future (search-summary context))
    (search-normal context)))


(defn- match
  [{{{:fhir.resource/keys [type]} :data} ::reitit/match
    ::reitit/keys [router]}
   name]
  (reitit/match-by-name router (keyword type name)))


(defn- self-link-url-fn [{:blaze/keys [base-url db] :as request} params]
  (fn [clauses offset]
    (nav/url base-url (match request "type") params clauses (iu/t db) offset)))


(defn- next-link-url-fn
  "Returns a function of `clauses`, `t` and `offset` that returns a
  CompletableFuture that will complete with the URL of the next link."
  [{:keys [page-store]}
   {:blaze/keys [base-url db]
    {{route-name :name} :data} ::reitit/match :as request}
   {:keys [token] :as params}]
  (if (or token (= "search" (some-> route-name name)))
    (fn [clauses offset]
      (nav/token-url page-store base-url (match request "page") params clauses
                     (iu/t db) offset))
    (fn [clauses offset]
      (ac/completed-future
        (nav/url base-url (match request "page") params clauses (iu/t db)
                 offset)))))


(defn- search-context
  [{:keys [page-store] :as context}
   {{{:fhir.resource/keys [type]} :data} ::reitit/match
    :keys [headers params]
    :blaze/keys [base-url db]
    ::reitit/keys [router]
    :as request}]
  (let [handling (handler-util/preference headers "handling")]
    (do-sync [params (params/decode page-store handling params)]
      (cond->
        (assoc context
          :blaze/base-url base-url
          :blaze/db db
          ::reitit/router router
          :type type
          :params params
          :self-link-url-fn (self-link-url-fn request params)
          :next-link-url-fn (next-link-url-fn context request params))
        handling
        (assoc :blaze.preference/handling handling)))))


(defn- handler [context]
  (fn [request]
    (-> (search-context context request)
        (ac/then-compose search)
        (ac/then-apply ring/response))))


(defmethod ig/pre-init-spec :blaze.interaction/search-type [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn :blaze/page-store]))


(defmethod ig/init-key :blaze.interaction/search-type [_ context]
  (log/info "Init FHIR search-type interaction handler")
  (-> (handler context)
      (wrap-observe-request-duration "search-type")))
