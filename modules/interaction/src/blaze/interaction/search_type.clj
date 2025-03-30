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
   [blaze.job.async-interaction.request :as req]
   [blaze.module :as m]
   [blaze.page-id-cipher.spec]
   [blaze.page-store :as page-store]
   [blaze.page-store.spec]
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

(defn- compile-type-query [db type clauses handling]
  (if (identical? :blaze.preference.handling/strict handling)
    (d/compile-type-query db type clauses)
    (d/compile-type-query-lenient db type clauses)))

(defn- execute-query [db query page-id]
  (if page-id
    (d/execute-query db query page-id)
    (d/execute-query db query)))

(defn- handles-and-clauses
  [{:blaze/keys [db] :keys [type] :blaze.preference/keys [handling]
    {:keys [clauses page-id]} :params}]
  (if (empty? clauses)
    {:handles (type-list db type page-id)}
    (when-ok [query (compile-type-query db type clauses handling)]
      {:handles (execute-query db query page-id)
       :query query})))

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

(defn- entries [context match-futures include-futures]
  (log/trace "build entries")
  (-> (into
       []
       (comp (mapcat ac/join)
             (map (partial search-util/entry context)))
       match-futures)
      (into
       (comp (mapcat ac/join)
             (map #(search-util/entry context % search-util/include)))
       include-futures)))

(defn- pull-matches-xf [type {:keys [summary elements]} db]
  (cond
    (seq elements)
    (map #(d/pull-many db % elements))

    (and (#{"CodeSystem" "ValueSet"} type) (not (#{"data" "false"} summary)))
    (map #(d/pull-many db % :summary))

    :else
    (map (partial d/pull-many db))))

(defn- total-future
  "Calculates the total number of resources returned.

  If we are on the first page (page-id is nil) and we don't have a next match,
  we can use number of matches.

  If we have no query (returning all resources), we can use `d/type-total`.

  If we are forced to calculate the total (total is accurate) we issue an
  `d/count-query`.

  Otherwise, we don't report it."
  [db type query {:keys [page-id total]} matches next-match]
  (cond
    ;; evaluate this criteria first, because we can potentially safe the
    ;; d/type-total call
    (and (nil? page-id) (nil? next-match))
    (ac/completed-future (count matches))

    (nil? query)
    (ac/completed-future (d/type-total db type))

    (= "accurate" total)
    (d/count-query db query)

    :else
    (ac/completed-future nil)))

(defn- page-data
  "Returns a CompletableFuture that will complete with a map of:

  :entries - the bundle entries of the page
  :num-matches - the number of search matches (excluding includes)
  :next-handle - the resource handle of the first resource of the next page
  :clauses - the actually used clauses

  or an anomaly in case of errors."
  {:arglists '([context])}
  [{:blaze/keys [db] :keys [type]
    {:keys [include-defs page-size] :as params} :params
    :as context}]
  (if-ok [{:keys [handles query]} (handles-and-clauses context)]
    (let [{:keys [matches includes next-match]}
          (build-page db include-defs page-size handles)
          total-future (total-future db type query params matches next-match)
          match-futures (into [] (comp (partition-all 100) (pull-matches-xf type params db)) matches)
          include-futures (into [] (comp (partition-all 100) (map (partial d/pull-many db))) includes)]
      (-> (ac/all-of (into (conj match-futures total-future) include-futures))
          (ac/exceptionally
           #(assoc %
                   ::anom/category ::anom/fault
                   :fhir/issue "incomplete"))
          (ac/then-apply
           (fn [_]
             {:entries (entries context match-futures include-futures)
              :next-handle next-match
              :total (ac/join total-future)
              :clauses (some-> query d/query-clauses)}))))
    ac/completed-future))

(defn- link [relation url]
  {:fhir/type :fhir.Bundle/link
   :relation relation
   :url (type/uri url)})

(defn- self-link [{:keys [self-link-url-fn]} clauses]
  (link "self" (self-link-url-fn clauses)))

(defn- first-link [{:keys [first-link-url-fn]} token clauses]
  (link "first" (first-link-url-fn token clauses)))

(defn- next-link-offset [next-handle]
  {"__page-id" (:id next-handle)})

(defn- next-link
  [{:keys [next-link-url-fn]} token clauses next-handle]
  (->> (next-link-url-fn token clauses (next-link-offset next-handle))
       (link "next")))

(defn- zero-bundle
  "Generate a special bundle if the search results in zero matches to avoid
  generating a token for the first link, we don't need in this case."
  [context clauses]
  {:fhir/type :fhir/Bundle
   :id (handler-util/luid context)
   :type #fhir/code"searchset"
   :total #fhir/unsignedInt 0
   :link [(self-link context clauses)]})

(defn- normal-bundle
  [{{{route-name :name} :data} ::reitit/match :as context} token clauses entries
   total]
  (cond->
   {:fhir/type :fhir/Bundle
    :id (handler-util/luid context)
    :type #fhir/code"searchset"
    :entry entries
    :link [(first-link context token clauses)]}
    (not= "page" (name route-name))
    (update :link conj (self-link context clauses))
    total
    (assoc :total (type/->UnsignedInt total))))

(defn- gen-token! [{{:keys [token]} :params :keys [gen-token-fn]} clauses]
  (if token
    (ac/completed-future token)
    (gen-token-fn clauses)))

(defn- search-normal [context]
  (-> (page-data context)
      (ac/then-compose
       (fn [{:keys [entries next-handle total clauses]}]
         (if (some-> total zero?)
           (ac/completed-future (zero-bundle context clauses))
           (do-sync [token (gen-token! context clauses)]
             (if next-handle
               (-> (normal-bundle context token clauses entries total)
                   (update :link conj (next-link context token clauses next-handle)))
               (normal-bundle context token clauses entries total))))))
      (ac/then-apply ring/response)))

(defn- summary-response [context total clauses]
  (ring/response
   {:fhir/type :fhir/Bundle
    :id (handler-util/luid context)
    :type #fhir/code"searchset"
    :total (type/->UnsignedInt total)
    :link [(self-link context clauses)]}))

(defn no-query-summary-response [{:keys [type] :blaze/keys [db] :as context}]
  (ac/completed-future (summary-response context (d/type-total db type) [])))

(defn- search-summary
  [{:keys [type] :blaze/keys [db] {:keys [clauses]} :params
    :blaze.preference/keys [handling] :as context}]
  (if (empty? clauses)
    (no-query-summary-response context)
    (if-ok [query (compile-type-query db type clauses handling)]
      (let [clauses (d/query-clauses query)]
        (if (empty? clauses)
          (no-query-summary-response context)
          (if (:blaze.preference/respond-async context)
            (req/handle-async context (:request context))
            (do-sync [total (d/count-query db query)]
              (summary-response context total clauses)))))
      ac/completed-future)))

(defn- search [{:keys [params] :as context}]
  (if (:summary? params)
    (search-summary context)
    (search-normal context)))

(defn- match
  [{{{:fhir.resource/keys [type]} :data} ::reitit/match
    ::reitit/keys [router]}
   name]
  (reitit/match-by-name router (keyword type name)))

(defn- page-match
  [{{{:fhir.resource/keys [type]} :data} ::reitit/match
    ::reitit/keys [router]}]
  (fn [page-id] (reitit/match-by-name router (keyword type "page") {:page-id page-id})))

(defn- self-link-url-fn [{:blaze/keys [base-url] :as request} params]
  (fn [clauses]
    (nav/url base-url (match request "type") params clauses)))

(defn- gen-token-fn
  [{:keys [page-store]} {{{route-name :name} :data} ::reitit/match}]
  (if (= "search" (name route-name))
    (fn [clauses]
      (if (empty? clauses)
        (ac/completed-future nil)
        (page-store/put! page-store clauses)))
    (fn [_clauses]
      (ac/completed-future nil))))

(defn- first-link-url-fn
  "Returns a function of `token` and `clauses` that returns the URL of the first
  link."
  [{:blaze/keys [base-url db] :as request} page-id-cipher params]
  (fn [token clauses]
    (nav/token-url base-url page-id-cipher (page-match request) params token clauses
                   (d/t db) nil)))

(defn- next-link-url-fn
  "Returns a function of `token`, `clauses` and `offset` that returns the URL
  of the next link."
  [{:blaze/keys [base-url db] :as request} page-id-cipher params]
  (fn [token clauses offset]
    (nav/token-url base-url page-id-cipher (page-match request) params token
                   clauses (d/t db) offset)))

(defn- search-context
  [{:keys [page-store page-id-cipher] :as context}
   {{{:fhir.resource/keys [type]} :data} ::reitit/match
    :keys [headers params]
    :blaze/keys [base-url db]
    ::reitit/keys [router match]
    :as request}]
  (let [handling (handler-util/preference headers "handling")
        respond-async (handler-util/preference headers "respond-async")]
    (do-sync [params (params/decode page-store type handling params)]
      (cond->
       (assoc context
              :blaze/base-url base-url
              :blaze/db db
              :request request
              ::reitit/router router
              ::reitit/match match
              :type type
              :params params
              :self-link-url-fn (self-link-url-fn request params)
              :gen-token-fn (gen-token-fn context request)
              :first-link-url-fn (first-link-url-fn request page-id-cipher params)
              :next-link-url-fn (next-link-url-fn request page-id-cipher params))
        handling
        (assoc :blaze.preference/handling handling)
        respond-async
        (assoc :blaze.preference/respond-async true)))))

(defmethod m/pre-init-spec :blaze.interaction/search-type [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn :blaze/page-store
                   :blaze/page-id-cipher]
          :opt-un [:blaze/context-path]))

(defmethod ig/init-key :blaze.interaction/search-type [_ context]
  (log/info "Init FHIR search-type interaction handler")
  (fn [request]
    (-> (search-context context request)
        (ac/then-compose search))))
