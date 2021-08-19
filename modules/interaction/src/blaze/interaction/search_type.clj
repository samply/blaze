(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.anomaly :as ba :refer [ex-anom if-ok when-ok]]
    [blaze.async.comp :as ac]
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
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
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
  [{:keys [type] :preference/keys [handling] :or {handling "strict"}
    {:keys [clauses page-id]} :params}
   db]
  (cond
    (empty? clauses)
    {:handles (type-list db type page-id)}

    (= "strict" handling)
    (when-ok [handles (type-query db type clauses page-id)]
      {:handles handles
       :clauses clauses})

    :else
    (let [query (d/compile-type-query-lenient db type clauses)]
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


(defn- entries [base-url router matches includes]
  (-> (mapv #(search-util/entry base-url router % search-util/match) matches)
      (into (map #(search-util/entry base-url router % search-util/include)) includes)))


(defn- page-data
  "Returns a CompletableFuture that will complete with a map of:

  :entries - the bundle entries of the page
  :num-matches - the number of search matches (excluding includes)
  :next-handle - the resource handle of the first resource of the next page
  :clauses - the actually used clauses"
  {:arglists '([context db])}
  [{:keys [base-url router] {:keys [include-defs page-size]} :params :as context} db]
  (let [{:keys [handles clauses] :as res} (handles-and-clauses context db)]
    (if (::anom/category res)
      (ac/failed-future (ex-anom res))
      (let [{:keys [matches includes next-match]}
            (build-page db include-defs page-size handles)
            match-futures (mapv #(d/pull-many db %) (partition-all 100 matches))
            include-futures (mapv #(d/pull-many db %) (partition-all 100 includes))]
        (-> (ac/all-of (into match-futures include-futures))
            (ac/then-apply
              (fn [_]
                {:entries
                 (entries
                   base-url
                   router
                   (mapcat deref match-futures)
                   (mapcat deref include-futures))
                 :num-matches (count matches)
                 :next-handle next-match
                 :clauses clauses})))))))


(defn- self-link-offset [first-entry]
  (when-let [id (-> first-entry :resource :id)]
    {"__page-id" id}))


(defn- self-link [{:keys [base-url match params]} clauses t first-entry]
  {:fhir/type :fhir.Bundle/link
   :relation "self"
   :url (type/->Uri (nav/url base-url match params clauses t
                             (self-link-offset first-entry)))})


(defn- next-link-offset [next-handle]
  {"__page-id" (:id next-handle)})


(defn- next-link [{:keys [base-url match params]} clauses t next-handle]
  {:fhir/type :fhir.Bundle/link
   :relation "next"
   :url (type/->Uri (nav/url base-url match params clauses t
                             (next-link-offset next-handle)))})


(defn- total
  "Calculates the total number of resources returned.

  If we have no clauses (returning all resources), we can use `d/type-total`.
  Secondly, if the number of entries found is not more than one page in size,
  we can use that number. Otherwise there is no cheap way to calculate the
  number of matching resources, so we don't report it."
  [db type {:keys [clauses page-id]} num-matches next-handle]
  (cond
    (empty? clauses)
    (d/type-total db type)

    (and (nil? page-id) (nil? next-handle))
    num-matches))


(defn- search-normal [{:keys [type params] :as context} db]
  (let [t (or (d/as-of-t db) (d/basis-t db))]
    (-> (page-data context db)
        (ac/then-apply
          (fn [{:keys [entries num-matches next-handle clauses]}]
            (let [total (total db type params num-matches next-handle)]
              (cond->
                {:fhir/type :fhir/Bundle
                 :id (iu/luid context)
                 :type #fhir/code"searchset"
                 :entry entries
                 :link [(self-link context clauses t (first entries))]}

                total
                (assoc :total (type/->UnsignedInt total))

                next-handle
                (update :link conj (next-link context clauses t next-handle)))))))))


(defn- summary-total
  [{:keys [type] :preference/keys [handling] :or {handling "strict"}
    {:keys [clauses]} :params}
   db]
  (cond
    (empty? clauses)
    {:total (d/type-total db type)}

    (= "strict" handling)
    (when-ok [handles (d/type-query db type clauses)]
      {:total (count handles)
       :clauses clauses})

    :else
    (let [query (d/compile-type-query-lenient db type clauses)]
      {:total (count (d/execute-query db query))
       :clauses (d/query-clauses query)})))


(defn- search-summary [context db]
  (let [t (or (d/as-of-t db) (d/basis-t db))
        {:keys [total clauses] :as summary-total} (summary-total context db)]
    (if (::anom/category summary-total)
      (ac/failed-future (ex-anom summary-total))
      (ac/completed-future
        {:fhir/type :fhir/Bundle
         :id (iu/luid context)
         :type #fhir/code"searchset"
         :total (type/->UnsignedInt total)
         :link [(self-link context clauses t [])]}))))


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
          (ac/then-apply ring/response)
          (ac/exceptionally handler-util/error-response))
      (comp ac/completed-future handler-util/error-response))))


(defmethod ig/pre-init-spec :blaze.interaction/search-type [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn]))


(defmethod ig/init-key :blaze.interaction/search-type [_ context]
  (log/info "Init FHIR search-type interaction handler")
  (-> (handler context)
      (wrap-observe-request-duration "search-type")))
